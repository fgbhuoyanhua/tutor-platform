package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tutor.platform.common.BizException;
import com.tutor.platform.entity.FavoriteEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.mapper.FavoriteMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.service.FavoriteService;
import com.tutor.platform.service.TutorService;
import com.tutor.platform.vo.TutorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final TutorMapper tutorMapper;
    private final TutorService tutorService;

    @Override
    public void add(Long studentId, Long tutorId) {
        TutorEntity tutor = tutorMapper.selectById(tutorId);
        if (tutor == null || tutor.getStatus() != 1) {
            throw new BizException(400, "老师不存在或未上架");
        }
        FavoriteEntity f = new FavoriteEntity();
        f.setStudentId(studentId);
        f.setTutorId(tutorId);
        f.setCreateTime(LocalDateTime.now());
        try {
            favoriteMapper.insert(f);
        } catch (DuplicateKeyException ignored) {
            // 已收藏，幂等
        }
    }

    @Override
    public void remove(Long studentId, Long tutorId) {
        favoriteMapper.delete(new LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getStudentId, studentId)
                .eq(FavoriteEntity::getTutorId, tutorId));
    }

    @Override
    public List<TutorVO> myFavorites(Long studentId) {
        List<FavoriteEntity> list = favoriteMapper.selectList(new LambdaQueryWrapper<FavoriteEntity>()
                .eq(FavoriteEntity::getStudentId, studentId)
                .orderByDesc(FavoriteEntity::getCreateTime));
        List<Long> tutorIds = list.stream().map(FavoriteEntity::getTutorId).collect(Collectors.toList());
        return tutorService.listByIds(tutorIds);
    }
}
