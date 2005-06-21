package org.openmicroscopy.omero.model;

import org.openmicroscopy.omero.BaseModelUtils;


import java.util.*;




/**
 * Image generated by hbm2java
 */
public class
Image 
implements java.io.Serializable ,
org.openmicroscopy.omero.OMEModel {

    // Fields    

     private Integer imageId;
     private Date inserted;
     private String name;
     private String description;
     private Date created;
     private String imageGuid;
     private Set thumbnails;
     private Set classifications;
     private Set displayRois;
     private Set imageInfos;
     private Set imagePixels;
     private Set imagePlates;
     private Set features;
     private Set imageAnnotations;
     private Set moduleExecutions;
     private Set imageDimensions;
     private Set channelComponents;
     private Set displayOptions;
     private ImagePixel imagePixel;
     private Group group;
     private Experimenter experimenter;
     private Set datasets;


    // Constructors

    /** default constructor */
    public Image() {
    }
    
    /** constructor with id */
    public Image(Integer imageId) {
        this.imageId = imageId;
    }
   
    
    

    // Property accessors

    /**
     * 
     */
    public Integer getImageId() {
        return this.imageId;
    }
    
    public void setImageId(Integer imageId) {
        this.imageId = imageId;
    }

    /**
     * 
     */
    public Date getInserted() {
        return this.inserted;
    }
    
    public void setInserted(Date inserted) {
        this.inserted = inserted;
    }

    /**
     * 
     */
    public String getName() {
        return this.name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 
     */
    public String getDescription() {
        return this.description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 
     */
    public Date getCreated() {
        return this.created;
    }
    
    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * 
     */
    public String getImageGuid() {
        return this.imageGuid;
    }
    
    public void setImageGuid(String imageGuid) {
        this.imageGuid = imageGuid;
    }

    /**
     * 
     */
    public Set getThumbnails() {
        return this.thumbnails;
    }
    
    public void setThumbnails(Set thumbnails) {
        this.thumbnails = thumbnails;
    }

    /**
     * 
     */
    public Set getClassifications() {
        return this.classifications;
    }
    
    public void setClassifications(Set classifications) {
        this.classifications = classifications;
    }

    /**
     * 
     */
    public Set getDisplayRois() {
        return this.displayRois;
    }
    
    public void setDisplayRois(Set displayRois) {
        this.displayRois = displayRois;
    }

    /**
     * 
     */
    public Set getImageInfos() {
        return this.imageInfos;
    }
    
    public void setImageInfos(Set imageInfos) {
        this.imageInfos = imageInfos;
    }

    /**
     * 
     */
    public Set getImagePixels() {
        return this.imagePixels;
    }
    
    public void setImagePixels(Set imagePixels) {
        this.imagePixels = imagePixels;
    }

    /**
     * 
     */
    public Set getImagePlates() {
        return this.imagePlates;
    }
    
    public void setImagePlates(Set imagePlates) {
        this.imagePlates = imagePlates;
    }

    /**
     * 
     */
    public Set getFeatures() {
        return this.features;
    }
    
    public void setFeatures(Set features) {
        this.features = features;
    }

    /**
     * 
     */
    public Set getImageAnnotations() {
        return this.imageAnnotations;
    }
    
    public void setImageAnnotations(Set imageAnnotations) {
        this.imageAnnotations = imageAnnotations;
    }

    /**
     * 
     */
    public Set getModuleExecutions() {
        return this.moduleExecutions;
    }
    
    public void setModuleExecutions(Set moduleExecutions) {
        this.moduleExecutions = moduleExecutions;
    }

    /**
     * 
     */
    public Set getImageDimensions() {
        return this.imageDimensions;
    }
    
    public void setImageDimensions(Set imageDimensions) {
        this.imageDimensions = imageDimensions;
    }

    /**
     * 
     */
    public Set getChannelComponents() {
        return this.channelComponents;
    }
    
    public void setChannelComponents(Set channelComponents) {
        this.channelComponents = channelComponents;
    }

    /**
     * 
     */
    public Set getDisplayOptions() {
        return this.displayOptions;
    }
    
    public void setDisplayOptions(Set displayOptions) {
        this.displayOptions = displayOptions;
    }

    /**
     * 
     */
    public ImagePixel getImagePixel() {
        return this.imagePixel;
    }
    
    public void setImagePixel(ImagePixel imagePixel) {
        this.imagePixel = imagePixel;
    }

    /**
     * 
     */
    public Group getGroup() {
        return this.group;
    }
    
    public void setGroup(Group group) {
        this.group = group;
    }

    /**
     * 
     */
    public Experimenter getExperimenter() {
        return this.experimenter;
    }
    
    public void setExperimenter(Experimenter experimenter) {
        this.experimenter = experimenter;
    }

    /**
     * 
     */
    public Set getDatasets() {
        return this.datasets;
    }
    
    public void setDatasets(Set datasets) {
        this.datasets = datasets;
    }





	/** utility methods. Container may re-assign this. */	
	protected static org.openmicroscopy.omero.BaseModelUtils _utils = 
		new org.openmicroscopy.omero.BaseModelUtils();
	public BaseModelUtils getUtils(){
		return _utils;
	}
	public void setUtils(BaseModelUtils utils){
		_utils = utils;
	}



}
