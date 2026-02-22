package com.abyss.orth.admin.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.xxl.tool.core.StringTool;

/**
 * Executor group entity for managing executor clusters.
 *
 * <p>Represents a logical group of executors that can run jobs, supporting both automatic
 * registration and manual configuration.
 */
public class JobGroup {

    /** Address type: Automatic registration via heartbeat */
    public static final int ADDRESS_TYPE_AUTO = 0;

    /** Address type: Manual configuration */
    public static final int ADDRESS_TYPE_MANUAL = 1;

    private int id;
    private String appname;
    private String title;
    private int addressType; // Executor address type (AUTO or MANUAL)
    private String addressList; // Executor addresses (comma-separated, for manual type)
    private Date updateTime;

    // Cached registry list for automatic registration
    private List<String> registryList;

    /**
     * Gets the list of registered executor addresses.
     *
     * @return list of executor addresses, parsed from addressList
     */
    public List<String> getRegistryList() {
        if (registryList == null && StringTool.isNotBlank(addressList)) {
            registryList = new ArrayList<>(Arrays.asList(addressList.split(",")));
        }
        return registryList;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getAddressType() {
        return addressType;
    }

    public void setAddressType(int addressType) {
        this.addressType = addressType;
    }

    public String getAddressList() {
        return addressList;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public void setAddressList(String addressList) {
        this.addressList = addressList;
    }
}
