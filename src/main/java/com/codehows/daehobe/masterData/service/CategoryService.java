package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.masterData.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public Category getCategoryById(Long id) {
        return id == null ? null :
                categoryRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
    }

    public List<MasterDataDto> findAll() {
        List<Category> categories = categoryRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (Category category : categories) {
            dtoList.add(new MasterDataDto(category.getId(), category.getName()));
        }
        return dtoList;
    }

    public Category createCategory(MasterDataDto masterDataDto) {
        String categoryName = masterDataDto.getName();

        // 중복 체크
        if (categoryRepository.existsByName(categoryName)) {
            throw new IllegalArgumentException("이미 존재하는 카테고리입니다: " + categoryName);
        }

        Category category = Category.builder()
                .name(categoryName)
                .build();

        try {
            return categoryRepository.save(category);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        categoryRepository.delete(category);
    }

    public Category updateCategory(Long id, MasterDataDto masterDataDto) {
        Category category = categoryRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        category.changeName(masterDataDto.getName());
        return category;
    }


}
