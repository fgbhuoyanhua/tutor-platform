package com.tutor.platform.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutor.platform.service.impl.TutorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 缓存序列化往返测试。
 *
 * 背景：为老师看板 / 学生列表加 Redis 缓存后，VO 需要能被 Jackson 反序列化。
 * 仅加 @Builder 的类没有无参构造，Jackson 读取缓存时会抛 InvalidDefinitionException，
 * 这类问题编译期发现不了，只能靠序列化往返测试兜住。
 */
class CacheSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("老师看板VO可JSON往返，字段无丢失")
    void tutorDashboardVORoundTrip() throws Exception {
        TutorDashboardVO vo = TutorDashboardVO.builder()
                .totalIncome(new BigDecimal("1234.56"))
                .totalOrders(22L)
                .completedOrders(3L)
                .pendingOrders(14L)
                .incomeTrend(new ArrayList<>())
                .subjectIncome(new ArrayList<>(List.of(
                        TutorDashboardVO.SubjectIncomeVO.builder()
                                .subjectId(2L).subjectName("数学")
                                .income(new BigDecimal("360.00")).orderCount(3L).build())))
                .statusDistribution(new ArrayList<>(List.of(
                        TutorDashboardVO.StatusCountVO.builder()
                                .status(3).statusName("已完成").count(3L).build())))
                .build();

        TutorDashboardVO back = objectMapper.readValue(
                objectMapper.writeValueAsString(vo), TutorDashboardVO.class);

        assertEquals(0, back.getTotalIncome().compareTo(new BigDecimal("1234.56")), "总收入应保持精度");
        assertEquals(22L, back.getTotalOrders());
        assertEquals(3L, back.getCompletedOrders());
        assertEquals(14L, back.getPendingOrders());
        assertEquals("数学", back.getSubjectIncome().get(0).getSubjectName());
        assertEquals(0, back.getSubjectIncome().get(0).getIncome().compareTo(new BigDecimal("360.00")));
        assertEquals("已完成", back.getStatusDistribution().get(0).getStatusName());
    }

    @Test
    @DisplayName("学生列表缓存载体可JSON往返，分页元数据与记录均保留")
    void cachedTutorPageRoundTrip() throws Exception {
        TutorServiceImpl.CachedTutorPage page = new TutorServiceImpl.CachedTutorPage();
        page.setPage(1);
        page.setSize(3);
        page.setTotal(6);
        page.setRecords(new ArrayList<>(List.of(
                TutorVO.builder()
                        .id(1L).userId(2L).tutorName("王老师")
                        .subjectName("英语").grade("高中")
                        .price(new BigDecimal("120.00"))
                        .rating(new BigDecimal("4.33"))
                        .evaluateCount(5L).finishedOrderCount(3L)
                        .build())));

        TutorServiceImpl.CachedTutorPage back = objectMapper.readValue(
                objectMapper.writeValueAsString(page), TutorServiceImpl.CachedTutorPage.class);

        assertEquals(1, back.getPage());
        assertEquals(3, back.getSize());
        assertEquals(6, back.getTotal());
        assertEquals(1, back.getRecords().size());

        TutorVO t = back.getRecords().get(0);
        assertNotNull(t, "记录不应为 null");
        assertEquals("王老师", t.getTutorName());
        assertEquals("英语", t.getSubjectName());
        assertEquals(0, t.getPrice().compareTo(new BigDecimal("120.00")));
        assertEquals(0, t.getRating().compareTo(new BigDecimal("4.33")));
        assertEquals(5L, t.getEvaluateCount());
    }

    @Test
    @DisplayName("脏缓存数据抛异常，由 Service 的 try-catch 降级查库")
    void dirtyCacheThrows() {
        assertThrows(Exception.class,
                () -> objectMapper.readValue("{这不是合法JSON", TutorDashboardVO.class),
                "非法JSON应抛异常，Service 层会捕获后降级");
    }

    @Test
    @DisplayName("所有作为接口返回的VO都必须具备无参构造（Jackson要求）")
    void allResponseVOsHaveNoArgsConstructor() {
        Class<?>[] responseVOs = {
                TutorVO.class, IncomeVO.class, OrderVO.class,
                EvaluationVO.class, LoginVO.class, MyStudentVO.class, TutorDashboardVO.class
        };
        for (Class<?> clazz : responseVOs) {
            assertNotNull(noArgsConstructor(clazz),
                    clazz.getSimpleName() + " 缺少无参构造，Jackson 无法反序列化");
        }
    }

    /** 返回无参构造，不存在时返回 null */
    private static java.lang.reflect.Constructor<?> noArgsConstructor(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
