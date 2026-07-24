package com.mett.hdr.project.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class ProjectSpaceUpdateRequest {

    @Size(max = 255)
    private String name;
    @Size(max = 64)
    private String parentGlobalSpaceId;
    private boolean parentGlobalSpaceIdPresent;
    @Size(max = 32)
    private String spaceLevelType;
    @Size(max = 64)
    private String usageCode;
    @Size(max = 32)
    private String geometryType;
    private BigDecimal lengthM;
    private BigDecimal widthM;
    private BigDecimal heightM;
    @Size(max = 32)
    private String orientationCode;
    private Integer sortOrder;

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String parentGlobalSpaceId() {
        return parentGlobalSpaceId;
    }

    @JsonSetter("parentGlobalSpaceId")
    public void setParentGlobalSpaceId(String parentGlobalSpaceId) {
        this.parentGlobalSpaceId = parentGlobalSpaceId;
        this.parentGlobalSpaceIdPresent = true;
    }

    public boolean parentGlobalSpaceIdPresent() {
        return parentGlobalSpaceIdPresent;
    }

    public String spaceLevelType() {
        return spaceLevelType;
    }

    public void setSpaceLevelType(String spaceLevelType) {
        this.spaceLevelType = spaceLevelType;
    }

    public String usageCode() {
        return usageCode;
    }

    public void setUsageCode(String usageCode) {
        this.usageCode = usageCode;
    }

    public String geometryType() {
        return geometryType;
    }

    public void setGeometryType(String geometryType) {
        this.geometryType = geometryType;
    }

    public BigDecimal lengthM() {
        return lengthM;
    }

    public void setLengthM(BigDecimal lengthM) {
        this.lengthM = lengthM;
    }

    public BigDecimal widthM() {
        return widthM;
    }

    public void setWidthM(BigDecimal widthM) {
        this.widthM = widthM;
    }

    public BigDecimal heightM() {
        return heightM;
    }

    public void setHeightM(BigDecimal heightM) {
        this.heightM = heightM;
    }

    public String orientationCode() {
        return orientationCode;
    }

    public void setOrientationCode(String orientationCode) {
        this.orientationCode = orientationCode;
    }

    public Integer sortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
