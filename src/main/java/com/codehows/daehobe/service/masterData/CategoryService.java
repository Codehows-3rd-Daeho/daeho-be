package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.repository.masterData.CategoryRepository;
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
        return categoryRepository.findById(id)
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

    public Long createCategory(MasterDataDto masterDataDto) {
        Category category = Category.builder()
                .name(masterDataDto.getName())
                .build();
        categoryRepository.save(category);
        return category.getId();
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        categoryRepository.delete(category);
    }
}
