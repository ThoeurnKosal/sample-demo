package com.mysample.demo.customer.infrastructure.client.jsonplaceholder;

import com.mysample.demo.customer.domain.CustomerDetails;
import com.mysample.demo.customer.domain.CustomerPost;
import com.mysample.demo.customer.domain.ExternalUser;
import com.mysample.demo.customer.infrastructure.client.jsonplaceholder.dto.JsonPlaceholderPost;
import com.mysample.demo.customer.infrastructure.client.jsonplaceholder.dto.JsonPlaceholderUser;
import com.mysample.demo.shared.mapping.MapStructConfig;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface JsonPlaceholderMapper {

    @Mapping(target = "externalId", source = "id")
    @Mapping(target = "details", source = "user")
    ExternalUser toDomain(JsonPlaceholderUser user);

    @Mapping(target = "fullName", source = "name")
    @Mapping(target = "city", source = "address.city")
    CustomerDetails toDetails(JsonPlaceholderUser user);

    CustomerPost toDomain(JsonPlaceholderPost post);

    List<CustomerPost> toDomain(List<JsonPlaceholderPost> posts);
}
