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

package org.activiti.cloud.services.organization.service;

import static org.activiti.cloud.organization.api.ProcessModelType.PROCESS;
import static org.activiti.cloud.organization.api.ValidationContext.EMPTY_CONTEXT;
import static org.activiti.cloud.services.common.util.ContentTypeUtils.CONTENT_TYPE_JSON;
import static org.activiti.cloud.services.common.util.ContentTypeUtils.JSON;
import static org.activiti.cloud.services.common.util.ContentTypeUtils.isJsonContentType;
import static org.activiti.cloud.services.common.util.ContentTypeUtils.removeExtension;
import static org.activiti.cloud.services.common.util.ContentTypeUtils.setExtension;
import static org.activiti.cloud.services.common.util.ContentTypeUtils.toJsonFilename;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.removeEnd;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.activiti.cloud.organization.api.Model;
import org.activiti.cloud.organization.api.ModelType;
import org.activiti.cloud.organization.api.Project;
import org.activiti.cloud.organization.api.ValidationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.activiti.bpmn.model.CallActivity;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.cloud.organization.api.*;
import org.activiti.cloud.organization.api.process.Extensions;
import org.activiti.cloud.organization.converter.JsonConverter;
import org.activiti.cloud.organization.core.error.ImportModelException;
import org.activiti.cloud.organization.core.error.UnknownModelTypeException;
import org.activiti.cloud.organization.repository.ModelRepository;
import org.activiti.cloud.services.common.file.FileContent;
import org.activiti.cloud.services.common.util.ContentTypeUtils;
import org.activiti.cloud.services.organization.converter.BpmnProcessModelContent;
import org.activiti.cloud.services.organization.validation.ProjectValidationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Business logic related to {@link Model} entities including process models, form models, connectors, data models and decision table models.
 */
@Service
@PreAuthorize("hasRole('ACTIVITI_MODELER')")
@Transactional
public class ModelService {

  private static final Logger logger = LoggerFactory.getLogger(ModelService.class);

  private final ModelRepository modelRepository;

  private final ModelTypeService modelTypeService;

  private final ModelContentService modelContentService;
  
  private final ModelExtensionsService modelExtensionsService;

  private final JsonConverter<Model> jsonConverter;

  private final HashMap<String, String> modelIdentifiers = new HashMap();

  private ObjectMapper objectMapper;

  @Autowired
  public ModelService(ModelRepository modelRepository,
                      ModelTypeService modelTypeService,
                      ModelContentService modelContentService,
                      ModelExtensionsService modelExtensionsService,
                      JsonConverter<Model> jsonConverter,
                      ObjectMapper objectMapper) {
    this.modelRepository = modelRepository;
    this.modelTypeService = modelTypeService;
    this.modelContentService = modelContentService;
    this.modelExtensionsService = modelExtensionsService;
    this.jsonConverter = jsonConverter;
    this.objectMapper = objectMapper;
  }

  public List<Model> getAllModels(Project project) {
    return modelTypeService.getAvailableModelTypes()
      .stream()
      .map(modelType -> getModels(project,
        modelType,
        Pageable.unpaged()))
      .map(Page::getContent)
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  public Page<Model> getModels(Project project,
                               ModelType modelType,
                               Pageable pageable) {
    return modelRepository.getModels(project,
      modelType,
      pageable);
  }

  public Model buildModel(String type,
                          String name) {
    try {
      Model model = (Model) modelRepository.getModelType().getConstructor().newInstance();
      model.setType(type);
      model.setName(name);
      return model;
    } catch (InstantiationException |
      IllegalAccessException |
      NoSuchMethodException |
      InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public Model createModel(Project project,
                           Model model) {
    model.setId(null);
    ModelType modelType = findModelType(model);
    model.setProject(project);
    if (model.getExtensions() == null && PROCESS.equals(modelType.getName())) {
      model.setExtensions(new Extensions().getAsMap());
    }
    return modelRepository.createModel(model);
  }

  public Model updateModel(Model modelToBeUpdated,
                           Model newModel) {
    return modelRepository.updateModel(modelToBeUpdated,
      newModel);
  }

  public void deleteModel(Model model) {
    modelRepository.deleteModel(model);
  }

  public Optional<Model> findModelById(String modelId) {
    return modelRepository.findModelById(modelId);
  }

  /**
   * THIS IS RELATED TO EXPORT - FIX IT
   * @param model
   * @return
   */
  public Optional<FileContent> getModelExtensionsFileContent(Model model) {
    if (model.getExtensions() == null && isJsonContentType(model.getContentType())) {
        return Optional.empty();
    }

    Model fullModel = findModelById(model.getId()).orElse(model);
    byte[] modelContent = modelRepository.getModelContent(model);
    final String bpmnModelId = modelContentService
      .findModelContentConverter(model.getType())
      .flatMap(converter -> converter.convertToModelContent(modelContent))
      .map(ModelContent::getId)
      .orElseGet(() -> modelContentService.getModelContentId(model));

    Model modelToFile = buildModel(fullModel.getType(), fullModel.getName());
    modelToFile.setId(bpmnModelId);
    modelToFile.setExtensions(fullModel.getExtensions());

    FileContent extensionsFileContent = new FileContent(getExtensionsFilename(model),
      CONTENT_TYPE_JSON,
      jsonConverter.convertToJsonBytes(modelToFile));
    return Optional.of(extensionsFileContent);
  }

  public void cleanModelIdList(){
    this.modelIdentifiers.clear();
  }

  public Model updateModelContent(Model modelToBeUpdate,
                                  FileContent fileContent) {
    FileContent fixedFileContent = this.modelIdentifiers.isEmpty()?
      fileContent:
      overrideModelContentId(modelToBeUpdate, fileContent);

    modelToBeUpdate.setContentType(fixedFileContent.getContentType());
    modelToBeUpdate.setContent(fixedFileContent.toString());

    Optional.ofNullable(modelToBeUpdate.getType())
      .flatMap(modelContentService::findModelContentConverter)
      .flatMap(validator -> validator.convertToModelContent(fixedFileContent.getFileContent()))
      .ifPresent(modelContent -> modelToBeUpdate.setTemplate(modelContent.getTemplate()));
    
    emptyIfNull(modelContentService.findContentUploadListeners(modelToBeUpdate.getType()))
    .stream()
    .forEach(listener -> listener.execute(modelToBeUpdate,fixedFileContent));

    return modelRepository.updateModelContent(modelToBeUpdate, fixedFileContent);
  }


  public FileContent overrideModelContentId(Model model, FileContent fileContent) {
    FileContent fixedFileContent = null;
    switch (model.getType()){
      case "PROCESS": {
        fixedFileContent = this.overridingProcessContentId(model, fileContent);
        break;
      }
      case "FORM":{
        fixedFileContent = this.overridingFormModelId(model, fileContent);
        break;
      }
      default:
        fixedFileContent = this.overridingModelID(model, fileContent);
    }
    return fixedFileContent;
  }

  private FileContent overridingProcessContentId(Model model, FileContent fileContent) {
    ModelContent modelContent = this.createBpmnModelContentFromModel(model, fileContent);
    this.fixProcessModel((BpmnProcessModelContent) modelContent);
    return new FileContent(fileContent.getFilename(), fileContent.getContentType(),
      this.convertModelContentToFile(modelContent, model));
  }

  private FileContent overridingModelID(Model model, FileContent fileContent) {
    try {
      ObjectNode jsonNode = (ObjectNode) objectMapper.readTree(fileContent.getFileContent());
      String actualId = this.modelIdentifiers.get(jsonNode.get("id").asText());
      if(actualId != null) {
        jsonNode.put("id", actualId);
      }
      return new FileContent(fileContent.getFilename(), fileContent.getContentType(), objectMapper.writeValueAsBytes(jsonNode));
    } catch (IOException e) {
      throw new ImportModelException(e);
    }
  }

  private FileContent overridingFormModelId(Model model, FileContent fileContent) {
    try {
      JsonNode jsonNode = objectMapper.readTree(fileContent.getFileContent());
      ObjectNode jFormRepresentation = (ObjectNode) jsonNode.get("formRepresentation");
      String actualId = this.modelIdentifiers.get(jFormRepresentation.get("id").asText());
      if(actualId != null) {
        jFormRepresentation.put("id", actualId);
      }
      return new FileContent(fileContent.getFilename(), fileContent.getContentType(), objectMapper.writeValueAsBytes(jsonNode));
    } catch (IOException e) {
      throw new ImportModelException(e);
    }
  }

    public Model newModelInstance() {
        try {
            return (Model) modelRepository.getModelType().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public String getExtensionsFilename(Model model) {
        return toJsonFilename(model.getName() + findModelType(model).getExtensionsFileSuffix());
    }

    public FileContent getModelContentFile(Model model) {
        return getModelFileContent(model,
                                   modelRepository.getModelContent(model));
    }

    public FileContent exportModel(Model model) {
        return getModelFileContent(model,
                                   modelRepository.getModelExport(model));
    }

    private FileContent getModelFileContent(Model model,
                                            byte[] modelBytes) {
        return new FileContent(setExtension(model.getName(),
                                            findModelType(model).getContentFileExtension()),
                               model.getContentType(),
                               modelBytes);
    }

    public Optional<FileContent> getModelDiagramFile(String modelId) {
        //TODO: to implement
        return Optional.empty();
    }

    

    public Optional<String> contentFilenameToModelName(String filename,
                                                       ModelType modelType) {
        return Arrays.stream(modelType.getAllowedContentFileExtension())
                .filter(filename::endsWith)
                .findFirst()
                .map(extension -> removeExtension(filename, extension));
    }

    public void validateModelContent(Model model,
                                     ValidationContext validationContext) {
        validateModelContent(model.getType(),
                             modelRepository.getModelContent(model),
                             validationContext);
    }

    public void validateModelContent(Model model,
                                     FileContent fileContent) {
        ValidationContext validationContext = !modelTypeService.isJson(findModelType(model)) && fileContent.getContentType().equals(CONTENT_TYPE_JSON)
                ? EMPTY_CONTEXT
                : Optional.ofNullable(model.getProject()).map(this::createValidationContext).orElseGet(() -> createValidationContext(model));

            validateModelContent(model.getType(),
                                 fileContent.getFileContent(),
                                 validationContext);     
    }
    
    private ValidationContext createValidationContext(Project project) {
        return new ProjectValidationContext(getAllModels(project));
    }

    private ValidationContext createValidationContext(Model model) {
        return new ProjectValidationContext(model);
    }

    public void validateModelContent(Model model,
                                     FileContent fileContent,
                                     ValidationContext validationContext) {
        validateModelContent(model.getType(),
                             fileContent.getFileContent(),
                             validationContext);
    }

    private void validateModelContent(String modelType,
                                      byte[] modelContent,
                                      ValidationContext validationContext) {
        emptyIfNull(modelContentService.findModelValidators(modelType))
        .stream()
        .forEach(modelValidator -> modelValidator.validateModelContent(modelContent,
                                                                       validationContext));
    }

    public void validateModelExtensions(Model model,
                                        ValidationContext validationContext) {
        validateModelExtensions(model.getType(),
                                modelRepository.getModelContent(model),
                                validationContext);
    }

    public void validateModelExtensions(Model model,
                                        FileContent fileContent) {
        ValidationContext validationContext = !modelTypeService.isJson(findModelType(model))
                ? EMPTY_CONTEXT
                : Optional.ofNullable(model.getProject()).map(this::createValidationContext).orElseGet(() -> createValidationContext(model));
        validateModelExtensions(model.getType(),
                                fileContent.getFileContent(),
                                validationContext);
    }

    public void validateModelExtensions(Model model,
                                        FileContent fileContent,
                                        ValidationContext validationContext) {
        validateModelExtensions(model.getType(),
                                fileContent.getFileContent(),
                                validationContext);
    }

    private void validateModelExtensions(String modelType,
                                         byte[] modelContent,
                                         ValidationContext validationContext) {
        emptyIfNull(modelExtensionsService.findExtensionsValidators(modelType))
        .stream()
        .forEach(modelValidator -> modelValidator.validateModelExtensions(modelContent,
                                                                          validationContext));
  }

  private void fixProcessModel(BpmnProcessModelContent processModelContent){
    processModelContent.getBpmnModel().getProcesses().forEach(process -> {
      String validIdentifier = this.modelIdentifiers.get(process.getId());
      if(validIdentifier != null && validIdentifier != process.getId()){
        process.setId(validIdentifier);
      }
      process.getFlowElements().stream()
        .filter(flowElement -> this.isElementToFix(flowElement))
        .map(flowElement -> {
          CallActivity callActivity = ((CallActivity) flowElement);
          String targetProcessId = this.modelIdentifiers.get(callActivity.getCalledElement());
          callActivity.setCalledElement(targetProcessId);
          return flowElement;
        })
        .collect(Collectors.toList());
    });
  }

  private boolean isElementToFix(FlowElement flowElement){
    return flowElement instanceof CallActivity &&
      this.modelIdentifiers.get(((CallActivity)flowElement).getCalledElement()) != null;
  }

  public byte[] convertModelContentToFile(ModelContent modelContent, Model model) {
    return modelContentService.findModelContentConverter(model.getType())
      .map(modelContentConverter -> modelContentConverter.convertToBytes(modelContent)).get();
  }

  public BpmnProcessModelContent createBpmnModelContentFromModel(Model model, FileContent fileContent) {
    Optional bpmnModelContent = modelContentService.findModelContentConverter(model.getType())
      .map(modelContentConverter -> modelContentConverter.convertToModelContent(fileContent.getFileContent())).get();
    return (BpmnProcessModelContent) bpmnModelContent.get();
  }

  public ModelContent createModelContentFromModel(Model model, FileContent fileContent) {
    return (ModelContent) modelContentService.findModelContentConverter(model.getType())
      .map(modelContentConverter -> modelContentConverter.convertToModelContent(fileContent.getFileContent()))
      .orElse(Optional.empty()).get();
  }

  public Model importSingleModel(Project project,
                                 ModelType modelType,
                                 FileContent fileContent) {
    Model model = this.importModel(project, modelType, fileContent);
    return this.updateModelContent(model, fileContent);

  }
  public Model importModel(Project project,
                           ModelType modelType,
                           FileContent fileContent) {
    logger.debug(MessageFormat.format(
      "Importing model type {0} from file {1}: {2}",
      modelType,
      fileContent.getFilename(),
      fileContent));

    Model model = importModelFromContent(project, modelType, fileContent);
    return model;
  }

  public Model importModelFromContent(Project project, ModelType modelType, FileContent fileContent) {
    Model model = null;
    if(modelTypeService.isJson(modelType) || ContentTypeUtils.isJsonContentType(fileContent.getContentType())){
      model = convertContentToModel(modelType, fileContent);
    }else {
      model = createModelFromContent(modelType, fileContent);
    }
    String convertedId =  model.getId() != null ? model.getId() : retrieveModelIdFromModelContent(model, fileContent);
    createModel(project, model);
    if(convertedId!= null) {
      modelIdentifiers.put(convertedId, String.join("-", model.getType().toLowerCase(), model.getId() ));
    }
    return model;
  }

    private ModelType findModelType(Model model) {
        return Optional.ofNullable(model.getType()).flatMap(modelTypeService::findModelTypeByName)
                .orElseThrow(() -> new UnknownModelTypeException("Unknown model type: " + model.getType()));
    }

  private String retrieveModelIdFromModelContent(Model model, FileContent fileContent) {
    ModelContent modelContent = null;
    if(this.hasModelConverter(model)) {
      modelContent = this.createModelContentFromModel(model, fileContent);
    }
    return modelContent != null ? modelContent.getId() : null;
  }

  private boolean hasModelConverter(Model model) {
    return model.getType() != "DECISION";
  }

  public Model convertContentToModel(ModelType modelType, FileContent fileContent) {
    Model model = jsonConverter.tryConvertToEntity(fileContent.getFileContent())
      .orElseThrow(() -> new ImportModelException("Cannot convert json file content to model: " + fileContent));
    model.setName(removeEnd(removeExtension(fileContent.getFilename(),
      JSON),
      modelType.getExtensionsFileSuffix()));
    model.setType(modelType.getName());

    return model;
  }

  public Model createModelFromContent(ModelType modelType,
                                      FileContent fileContent) {
    return contentFilenameToModelName(fileContent.getFilename(),
      modelType)
      .map(modelName -> buildModel(modelType.getName(),
        modelName))
      .orElseThrow(() -> new ImportModelException(MessageFormat.format(
        "Unexpected extension was found for file to import model of type {0}: {1}",
        modelType.getName(),
        fileContent.getFilename())));
  }
}
