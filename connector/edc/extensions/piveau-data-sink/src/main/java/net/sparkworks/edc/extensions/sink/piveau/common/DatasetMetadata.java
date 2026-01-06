/*
 *  Copyright (c) 2024 SparkWorks
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       SparkWorks - initial implementation
 *
 */

package net.sparkworks.edc.extensions.sink.piveau.common;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents dataset metadata from JSON input that will be transformed to DCAT-AP format.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatasetMetadata {
    
    private String datasetId;
    
    @Getter(AccessLevel.NONE)
    private String title;
    
    @Getter(AccessLevel.NONE)
    private String description;
    
    @Setter(AccessLevel.NONE)
    private List<String> keywords = new ArrayList<>();
    
    private String theme = "TECH";
    
    private String license = "https://creativecommons.org/licenses/by/4.0/";
    
    private String issued;
    
    private String modified;
    
    private String catalogue;
    
    // from eur
    private String publisher;
    
    @JsonProperty("record_count")
    @JsonAlias({"recordCount"})
    private String recordCount;
    
    @JsonProperty("file_format")
    @JsonAlias({"fileFormat"})
    private String fileFormat;
    
    private List<String> columns;
    
    @JsonProperty("number_of_files")
    @JsonAlias({"numberOfFiles"})
    private Integer number_of_files;
    
    // Custom getters with logic
    
    public String getTitle() {
        return title != null ? title : datasetId;
    }
    
    public String getDescription() {
        return description != null ? description : getTitle();
    }
    
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords != null ? keywords : new ArrayList<>();
    }
}
