package com.start.overflow.catalog.mapper;

import com.start.overflow.catalog.dto.CategoryResponse;
import com.start.overflow.catalog.dto.CategorySummaryResponse;
import com.start.overflow.catalog.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    CategorySummaryResponse toSummary(Category category);
}
