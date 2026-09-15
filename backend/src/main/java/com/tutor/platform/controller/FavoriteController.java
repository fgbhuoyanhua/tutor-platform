package com.tutor.platform.controller;

import com.tutor.platform.common.ApiResponse;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.security.RequireRole;
import com.tutor.platform.service.FavoriteService;
import com.tutor.platform.vo.TutorVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生收藏老师
 */
@Tag(name = "收藏")
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏老师")
    @PostMapping("/{tutorId}")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Void> add(@PathVariable Long tutorId) {
        favoriteService.add(UserContext.getUid(), tutorId);
        return ApiResponse.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{tutorId}")
    @RequireRole({UserContext.ROLE_STUDENT})
    public ApiResponse<Void> remove(@PathVariable Long tutorId) {
        favoriteService.remove(UserContext.getUid(), tutorId);
        return ApiResponse.ok();
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping
    public ApiResponse<List<TutorVO>> myFavorites() {
        return ApiResponse.ok(favoriteService.myFavorites(UserContext.getUid()));
    }
}
