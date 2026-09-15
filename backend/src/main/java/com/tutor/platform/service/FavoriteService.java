package com.tutor.platform.service;

import com.tutor.platform.vo.TutorVO;

import java.util.List;

/**
 * 学生收藏老师
 */
public interface FavoriteService {

    /** 收藏（幂等） */
    void add(Long studentId, Long tutorId);

    /** 取消收藏 */
    void remove(Long studentId, Long tutorId);

    /** 我收藏的老师完整信息 */
    List<TutorVO> myFavorites(Long studentId);
}
