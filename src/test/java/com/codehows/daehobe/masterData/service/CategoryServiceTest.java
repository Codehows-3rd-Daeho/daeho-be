package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.masterData.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * <p>CategoryService에 대한 단위 테스트 클래스입니다.</p>
 *
 * <p>MockitoExtension을 사용하여 Mockito 어노테이션을 활성화하고,
 * {@code @Mock}을 통해 `CategoryRepository`를 Mock 객체로 주입하여
 * `CategoryService`의 비즈니스 로직을 격리하여 테스트합니다.</p>
 */
@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category testCategory;
    private Long categoryId = 1L;
    private String categoryName = "테스트카테고리";

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(categoryId)
                .name(categoryName)
                .build();
    }

    @Test
    @DisplayName("성공: ID로 카테고리 조회")
    void getCategoryById_Success() {
        // given
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

        // when
        Category foundCategory = categoryService.getCategoryById(categoryId);

        // then
        assertThat(foundCategory).isNotNull();
        assertThat(foundCategory.getId()).isEqualTo(categoryId);
        assertThat(foundCategory.getName()).isEqualTo(categoryName);
        verify(categoryRepository).findById(categoryId);
    }

    @Test
    @DisplayName("실패: ID로 카테고리 조회 시 카테고리 없음")
    void getCategoryById_NotFound() {
        // given
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(RuntimeException.class, () -> categoryService.getCategoryById(categoryId));
        verify(categoryRepository).findById(categoryId);
    }

    @Test
    @DisplayName("성공: 모든 카테고리 조회")
    void findAll_Success() {
        // given
        Category cat1 = Category.builder().id(1L).name("카테고리1").build();
        Category cat2 = Category.builder().id(2L).name("카테고리2").build();
        List<Category> categoryList = Arrays.asList(cat1, cat2);
        when(categoryRepository.findAll()).thenReturn(categoryList);

        // when
        List<MasterDataDto> dtoList = categoryService.findAll();

        // then
        assertThat(dtoList).hasSize(2);
        assertThat(dtoList.get(0).getName()).isEqualTo("카테고리1");
        verify(categoryRepository).findAll();
    }
    
    @Test
    @DisplayName("성공: 카테고리 목록이 비어있을 때 모든 카테고리 조회")
    void findAll_EmptyList() {
        // given
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>());

        // when
        List<MasterDataDto> dtoList = categoryService.findAll();

        // then
        assertThat(dtoList).isNotNull();
        assertThat(dtoList).isEmpty();
        verify(categoryRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("성공: 카테고리 생성")
    void createCategory_Success() {
        // given
        MasterDataDto newCatDto = new MasterDataDto(null, "새카테고리");
        Category savedCategory = Category.builder().id(2L).name("새카테고리").build();
        when(categoryRepository.existsByName(newCatDto.getName())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        // when
        Category createdCategory = categoryService.createCategory(newCatDto);

        // then
        assertThat(createdCategory.getName()).isEqualTo("새카테고리");
        verify(categoryRepository).existsByName(newCatDto.getName());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("실패: 중복 이름으로 카테고리 생성 시 IllegalArgumentException")
    void createCategory_DuplicateName_ThrowsException() {
        // given
        MasterDataDto newCatDto = new MasterDataDto(null, categoryName);
        when(categoryRepository.existsByName(categoryName)).thenReturn(true);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> categoryService.createCategory(newCatDto));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("성공: 카테고리 삭제")
    void deleteCategory_Success() {
        // given
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        doNothing().when(categoryRepository).delete(testCategory);

        // when
        categoryService.deleteCategory(categoryId);

        // then
        verify(categoryRepository).delete(testCategory);
    }

    @Test
    @DisplayName("실패: 삭제할 카테고리 없음 시 EntityNotFoundException")
    void deleteCategory_NotFound_ThrowsException() {
        // given
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> categoryService.deleteCategory(categoryId));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    @DisplayName("성공: 카테고리 이름 업데이트")
    void updateCategory_Success() {
        // given
        String newName = "업데이트카테고리";
        MasterDataDto updateDto = new MasterDataDto(categoryId, newName);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

        // when
        Category updatedCategory = categoryService.updateCategory(categoryId, updateDto);

        // then
        assertThat(updatedCategory.getName()).isEqualTo(newName);
        verify(categoryRepository).findById(categoryId);
    }

    @Test
    @DisplayName("실패: 업데이트할 카테고리 없음 시 EntityNotFoundException")
    void updateCategory_NotFound_ThrowsException() {
        // given
        MasterDataDto updateDto = new MasterDataDto(categoryId, "새이름");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> categoryService.updateCategory(categoryId, updateDto));
    }
}
