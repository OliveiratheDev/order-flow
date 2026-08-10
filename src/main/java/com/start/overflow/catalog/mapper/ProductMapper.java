package com.start.overflow.catalog.mapper;

import com.start.overflow.catalog.dto.ProductResponse;
import com.start.overflow.catalog.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = CategoryMapper.class)
public interface ProductMapper {
    ProductResponse toResponse(Product product);
}
