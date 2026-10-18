package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutor.platform.common.BizException;
import com.tutor.platform.entity.SubjectEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.EvaluationMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.service.TutorService;
import com.tutor.platform.vo.TutorVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 家教信息服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorServiceImpl implements TutorService {

    private final TutorMapper tutorMapper;
    private final UserMapper userMapper;
    private final SubjectMapper subjectMapper;
    private final EvaluationMapper evaluationMapper;
    private final AppointmentMapper appointmentMapper;
    private final MessageService messageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Long publish(Long userId, TutorEntity tutor) {
        validateTutor(tutor);
        tutor.setId(null);
        tutor.setUserId(userId);
        tutor.setRating(new BigDecimal("5.00"));
        tutor.setStatus(0); // 待审核
        tutor.setCreateTime(LocalDateTime.now());
        tutorMapper.insert(tutor);
        return tutor.getId();
    }

    @Override
    public void update(Long userId, TutorEntity tutor) {
        TutorEntity old = tutorMapper.selectById(tutor.getId());
        if (old == null || !old.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该家教信息");
        }
        validateTutor(tutor);
        tutor.setUserId(userId);
        // 编辑后重新进入待审核
        tutor.setStatus(0);
        tutorMapper.updateById(tutor);
    }

    /** 家教信息参数校验：资费、简介、学段 */
    private void validateTutor(TutorEntity tutor) {
        if (tutor.getPrice() == null || tutor.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "资费必须大于0");
        }
        if (tutor.getPrice().compareTo(new BigDecimal("9999")) > 0) {
            throw new BizException(400, "资费不能超过9999元/时");
        }
        if (tutor.getSubjectId() == null) {
            throw new BizException(400, "授课科目不能为空");
        }
        if (tutor.getGrade() != null && tutor.getGrade().length() > 50) {
            throw new BizException(400, "辅导学段不能超过50字");
        }
        if (tutor.getIntroduce() != null && tutor.getIntroduce().length() > 2000) {
            throw new BizException(400, "个人简介不能超过2000字");
        }
    }

    /**
     * 老师列表分页查询（仅已上架的老师）
     * 
     * 性能优化：加Redis缓存，缓存Key为 tutor:list:{page}:{size}:{subjectId}:{priceMin}:{priceMax}:{keyword}，过期时间3分钟
     * 理由：学生每次打开首页都会调用此接口，涉及多次数据库查询，加缓存可大幅减少数据库压力
     * 
     * @param page 页码
     * @param size 每页数量
     * @param subjectId 科目ID（可选）
     * @param priceMin 最低价格（可选）
     * @param priceMax 最高价格（可选）
     * @param keyword 关键词（可选）
     * @return 分页结果
     */
    @Override
    public IPage<TutorVO> pageList(long page, long size, Long subjectId,
                                   BigDecimal priceMin, BigDecimal priceMax, String keyword) {
        // 生成缓存Key
        String cacheKey = String.format("tutor:list:%d:%d:%s:%s:%s:%s",
            page, size, subjectId, priceMin, priceMax, keyword);

        // 先查缓存：把分页结果转成纯数据对象再序列化，避免直接缓存 IPage 实现类
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                CachedTutorPage hit = objectMapper.readValue(cached, CachedTutorPage.class);
                Page<TutorVO> p = new Page<>(hit.getPage(), hit.getSize(), hit.getTotal());
                p.setRecords(hit.getRecords());
                return p;
            }
        } catch (Exception e) {
            log.debug("家教列表缓存读取失败，降级查库: {}", e.getMessage());
        }

        // 缓存未命中，查数据库
        LambdaQueryWrapper<TutorEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(TutorEntity::getStatus, 1) // 仅已上架
           .eq(subjectId != null, TutorEntity::getSubjectId, subjectId)
           .ge(priceMin != null, TutorEntity::getPrice, priceMin)
           .le(priceMax != null, TutorEntity::getPrice, priceMax)
           .like(StringUtils.hasText(keyword), TutorEntity::getIntroduce, keyword)
           .orderByDesc(TutorEntity::getRating);

        IPage<TutorEntity> p = tutorMapper.selectPage(new Page<>(page, size), qw);
        List<TutorEntity> records = p.getRecords();
        Map<Long, Long> evalCount = countMap(evaluationMapper.countGroupByTutorIds(userIds(records)), records);
        Map<Long, Long> finishedCount = countMap(appointmentMapper.countFinishedGroupByTutorIds(userIds(records)), records);
        IPage<TutorVO> result = p.convert(t -> toVO(t, evalCount, finishedCount));

        // 写入缓存，过期时间3分钟；写失败不影响返回结果
        try {
            CachedTutorPage payload = new CachedTutorPage();
            payload.setPage(result.getCurrent());
            payload.setSize(result.getSize());
            payload.setTotal(result.getTotal());
            payload.setRecords(result.getRecords());
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(payload), 180, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("家教列表缓存写入失败，已忽略: {}", e.getMessage());
        }
        return result;
    }

    /** 家教列表缓存载体：只保留分页元数据与记录，规避 IPage 实现类无法序列化的问题 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CachedTutorPage {
        private long page;
        private long size;
        private long total;
        private List<TutorVO> records;
    }

    @Override
    public TutorVO detail(Long id) {
        TutorEntity t = tutorMapper.selectById(id);
        if (t == null || t.getStatus() != 1) {
            throw new BizException("家教信息不存在或未上架");
        }
        List<TutorEntity> one = List.of(t);
        Map<Long, Long> evalCount = countMap(evaluationMapper.countGroupByTutorIds(List.of(t.getUserId())), one);
        Map<Long, Long> finishedCount = countMap(appointmentMapper.countFinishedGroupByTutorIds(List.of(t.getUserId())), one);
        return toVO(t, evalCount, finishedCount);
    }

    @Override
    public List<TutorVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<TutorEntity> list = tutorMapper.selectBatchIds(ids);
        Map<Long, Long> evalCount = countMap(evaluationMapper.countGroupByTutorIds(userIds(list)), list);
        Map<Long, Long> finishedCount = countMap(appointmentMapper.countFinishedGroupByTutorIds(userIds(list)), list);
        return list.stream().map(t -> toVO(t, evalCount, finishedCount)).collect(Collectors.toList());
    }

    @Override
    public void audit(Long id, boolean pass, String reason) {
        TutorEntity t = tutorMapper.selectById(id);
        if (t == null) {
            throw new BizException("家教信息不存在");
        }
        if (t.getStatus() != 0) {
            throw new BizException("仅待审核状态可审核");
        }
        t.setStatus(pass ? 1 : 3);
        tutorMapper.updateById(t);
        // 审核驳回时站内消息通知老师（系统消息，fromId=0）
        if (!pass) {
            messageService.send(0L, t.getUserId(), 3,
                    "您的家教信息审核未通过" + (StringUtils.hasText(reason) ? "，原因：" + reason : "，请修改后重新提交"));
        }
    }

    @Override
    public List<TutorVO> recommend(Long subjectId, BigDecimal priceMax, int limit) {
        LambdaQueryWrapper<TutorEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(TutorEntity::getStatus, 1)
           .eq(subjectId != null, TutorEntity::getSubjectId, subjectId)
           .le(priceMax != null, TutorEntity::getPrice, priceMax)
           .orderByDesc(TutorEntity::getRating)
           .last("LIMIT " + limit);
        List<TutorEntity> list = tutorMapper.selectList(qw);
        Map<Long, Long> evalCount = countMap(evaluationMapper.countGroupByTutorIds(userIds(list)), list);
        Map<Long, Long> finishedCount = countMap(appointmentMapper.countFinishedGroupByTutorIds(userIds(list)), list);
        return list.stream().map(t -> toVO(t, evalCount, finishedCount)).collect(Collectors.toList());
    }

    /** 提取家教记录中的老师 user.id 列表（去重） */
    private List<Long> userIds(List<TutorEntity> records) {
        return records.stream()
                .map(TutorEntity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 把 mapper 返回的聚合行转为 userId -> count，空查询返回空 Map */
    private Map<Long, Long> countMap(List<Map<String, Object>> rows, List<TutorEntity> records) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r.get("tutorId")).longValue(),
                r -> ((Number) r.get("cnt")).longValue()));
    }

    private TutorVO toVO(TutorEntity t, Map<Long, Long> evalCount, Map<Long, Long> finishedCount) {
        UserEntity u = userMapper.selectById(t.getUserId());
        SubjectEntity s = subjectMapper.selectById(t.getSubjectId());
        Long evalCnt = evalCount == null ? null : evalCount.get(t.getUserId());
        Long finCnt = finishedCount == null ? null : finishedCount.get(t.getUserId());
        return TutorVO.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .tutorName(u == null ? null : u.getRealName())
                .avatar(u == null ? null : u.getAvatar())
                .subjectId(t.getSubjectId())
                .subjectName(s == null ? null : s.getName())
                .grade(t.getGrade())
                .price(t.getPrice())
                .rating(t.getRating())
                .evaluateCount(evalCnt == null ? 0L : evalCnt)
                .finishedOrderCount(finCnt == null ? 0L : finCnt)
                .introduce(t.getIntroduce())
                .status(t.getStatus())
                .createTime(t.getCreateTime())
                .build();
    }
}
