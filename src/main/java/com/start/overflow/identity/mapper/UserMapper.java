package com.start.overflow.identity.mapper;

import com.start.overflow.identity.dto.UserResponse;
import com.start.overflow.identity.entity.AppUser;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(AppUser user);
}
