/*
 * Copyright 2018 Alfresco, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.cloud.services.organization.rest.config;

import org.activiti.cloud.alfresco.data.domain.ExtendedPageMetadataConverter;
import org.activiti.cloud.services.organization.rest.assembler.ModelResourceAssembler;
import org.activiti.cloud.services.organization.rest.assembler.ModelTypeRelProvider;
import org.activiti.cloud.services.organization.rest.assembler.ModelTypeResourceAssembler;
import org.activiti.cloud.services.organization.rest.assembler.PagedModelTypeAssembler;
import org.activiti.cloud.services.organization.rest.assembler.ProjectResourceAssembler;
import org.activiti.cloud.services.organization.rest.assembler.ValidationErrorResourceAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.hateoas.mvc.TypeConstrainedMappingJackson2HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UriComponents;

import java.util.List;

/**
 * Extended WebMvcConfigurer
 */
@Configuration
public class ExtendedWebMvcConfigurer implements WebMvcConfigurer {

    private final Jackson2ObjectMapperBuilder objectMapperBuilder;

    @Autowired
    public ExtendedWebMvcConfigurer(Jackson2ObjectMapperBuilder objectMapperBuilder) {
        this.objectMapperBuilder = objectMapperBuilder;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .filter(converter -> !(converter instanceof TypeConstrainedMappingJackson2HttpMessageConverter))
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .map(MappingJackson2HttpMessageConverter::getObjectMapper)
                .forEach(objectMapperBuilder::configure);
    }
    
    @Bean
    public ModelResourceAssembler ModelResourceAssembler() {
        return new ModelResourceAssembler();
    }
    
    @Bean
    public ModelTypeRelProvider modelTypeRelProvider() {
        return new ModelTypeRelProvider();
    }

    @Bean
    public ModelTypeResourceAssembler modelTypeResourceAssembler() {
        return new ModelTypeResourceAssembler();
    }

    @Bean
    public PagedModelTypeAssembler pagedModelTypeAssembler(@Nullable HateoasPageableHandlerMethodArgumentResolver resolver,
                                                           @Nullable UriComponents baseUri,
                                                           ExtendedPageMetadataConverter extendedPageMetadataConverter) {
        return new PagedModelTypeAssembler(resolver,
                                           baseUri,
                                           extendedPageMetadataConverter);
    }

    @Bean
    public ProjectResourceAssembler projectResourceAssembler() {
        return new ProjectResourceAssembler();
    }
    
    @Bean
    public ValidationErrorResourceAssembler ValidationErrorResourceAssembler() {
        return new ValidationErrorResourceAssembler();
    }    
}
