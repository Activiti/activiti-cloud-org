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

package org.activiti.cloud.organization.api;

import static org.activiti.cloud.services.common.util.ContentTypeUtils.CONTENT_TYPE_JSON;

/**
 * Business logic related with validation of the metadata of a model
 */
public interface MetadataValidator {
    
    /**
     * Validate the given model content.
     * @param modelContent the model content to validate
     * @param validationContext the validation context
     */
    void validateModelContent(byte[] modelContent,
                              ValidationContext validationContext);

    /**
     * Get handled model type by this validator.
     * @return handled model type
     */
    ModelType getHandledModelType();
    
    
    /**
     * Get handled content type by this validator (by default JSON).
     * 
     * @return handled content type
     */
    default String getHandledContentType() {
        return CONTENT_TYPE_JSON;
    }
}