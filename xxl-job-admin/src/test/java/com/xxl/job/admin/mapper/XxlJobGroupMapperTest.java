package com.xxl.job.admin.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.xxl.job.admin.model.XxlJobGroup;

import jakarta.annotation.Resource;

/**
 * Integration tests for {@link XxlJobGroupMapper}.
 *
 * <p>Tests CRUD operations for executor groups in the Orth distributed task scheduling system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class XxlJobGroupMapperTest {

    // Test data constants
    private static final String TEST_APP_NAME = "test-app-executor";
    private static final String TEST_TITLE = "Test Executor Group";
    private static final String TEST_ADDRESS_LIST = "http://localhost:9999";
    private static final int TEST_ADDRESS_TYPE_AUTO = 0;

    private static final String TEST_APP_NAME_UPDATED = "test-app-executor-updated";
    private static final String TEST_TITLE_UPDATED = "Test Executor Group Updated";
    private static final String TEST_ADDRESS_LIST_UPDATED = "http://localhost:8888";
    private static final int TEST_ADDRESS_TYPE_MANUAL = 2;

    @Resource private XxlJobGroupMapper xxlJobGroupMapper;

    /**
     * Tests complete CRUD lifecycle for job executor groups.
     *
     * <p>Verifies:
     *
     * <ul>
     *   <li>Querying all groups and filtering by address type
     *   <li>Creating new executor group
     *   <li>Loading group by ID
     *   <li>Updating group properties
     *   <li>Deleting group
     * </ul>
     */
    @Test
    public void testJobGroupCrudOperations() {
        // Query operations
        List<XxlJobGroup> allGroups = xxlJobGroupMapper.findAll();
        assertNotNull(allGroups);

        List<XxlJobGroup> autoDiscoveryGroups = xxlJobGroupMapper.findByAddressType(0);
        assertNotNull(autoDiscoveryGroups);

        // Create new group
        XxlJobGroup group = createTestGroup();
        int saveResult = xxlJobGroupMapper.save(group);
        assertEquals(1, saveResult, "Save should affect 1 row");
        assertNotNull(group.getId(), "Group ID should be generated");

        // Load and verify
        XxlJobGroup loadedGroup = xxlJobGroupMapper.load(group.getId());
        assertNotNull(loadedGroup);
        assertEquals(TEST_APP_NAME, loadedGroup.getAppname());

        // Update group
        updateGroupProperties(loadedGroup);
        int updateResult = xxlJobGroupMapper.update(loadedGroup);
        assertEquals(1, updateResult, "Update should affect 1 row");

        // Delete group
        int deleteResult = xxlJobGroupMapper.remove(group.getId());
        assertEquals(1, deleteResult, "Delete should affect 1 row");
    }

    /**
     * Creates a test executor group with default test data.
     *
     * @return configured test group instance
     */
    private XxlJobGroup createTestGroup() {
        XxlJobGroup group = new XxlJobGroup();
        group.setAppname(TEST_APP_NAME);
        group.setTitle(TEST_TITLE);
        group.setAddressType(TEST_ADDRESS_TYPE_AUTO);
        group.setAddressList(TEST_ADDRESS_LIST);
        group.setUpdateTime(new Date());
        return group;
    }

    /**
     * Updates group properties with modified test data.
     *
     * @param group the group to update
     */
    private void updateGroupProperties(XxlJobGroup group) {
        group.setAppname(TEST_APP_NAME_UPDATED);
        group.setTitle(TEST_TITLE_UPDATED);
        group.setAddressType(TEST_ADDRESS_TYPE_MANUAL);
        group.setAddressList(TEST_ADDRESS_LIST_UPDATED);
        group.setUpdateTime(new Date());
    }
}
