package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.Category;
import com.codehows.daehobe.repository.CategoryRepository;
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

    public List<MasterDataDto> findAll() {
        List<Category> categories = categoryRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (Category category : categories) {
            dtoList.add(new MasterDataDto(category.getId(), category.getName()));
        }
        return dtoList;
    }
}
