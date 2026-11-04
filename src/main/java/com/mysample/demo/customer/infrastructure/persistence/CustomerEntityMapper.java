package com.mysample.demo.customer.infrastructure.persistence;


import com.mysample.demo.customer.domain.Customer;
import com.mysample.demo.customer.domain.CustomerDetails;
import com.mysample.demo.shared.mapping.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
interface CustomerEntityMapper {

    @Mapping(target = "fullName", source = "details.fullName")
    @Mapping(target = "email", source = "details.email")
    @Mapping(target = "phone", source = "details.phone")
    @Mapping(target = "city", source = "details.city")
    @BeanMapping(ignoreUnmappedSourceProperties = {"linkedToDirectory"})
    CustomerEntity toEntity(Customer customer);

    @Mapping(target = "details", source = "entity")
    Customer toDomain(CustomerEntity entity);

    @BeanMapping(ignoreUnmappedSourceProperties = {
            "id", "version", "externalId", "createdAt", "updatedAt"
    })
    CustomerDetails toDetails(CustomerEntity entity);
}
