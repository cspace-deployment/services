/**
 *  This document is a part of the source code and related artifacts
 *  for CollectionSpace, an open source collections management system
 *  for museums and related institutions:

 *  http://www.collectionspace.org
 *  http://wiki.collectionspace.org

 *  Copyright 2009 University of California at Berkeley

 *  Licensed under the Educational Community License (ECL), Version 2.0.
 *  You may not use this file except in compliance with this License.

 *  You may obtain a copy of the ECL 2.0 License at

 *  https://source.collectionspace.org/collection-space/LICENSE.txt

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.collectionspace.services.authorization.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.collectionspace.services.authorization.PermissionRole;
import org.collectionspace.services.authorization.PermissionRoleSubResource;
import org.collectionspace.services.authorization.PermissionValue;
import org.collectionspace.services.authorization.Role;
import org.collectionspace.services.authorization.RoleValue;
import org.collectionspace.services.authorization.RolesList;
import org.collectionspace.services.authorization.SubjectType;

import org.collectionspace.services.client.PermissionRoleFactory;
import org.collectionspace.services.client.RoleClient;
import org.collectionspace.services.client.RoleFactory;
import org.collectionspace.services.common.api.Tools;
import org.collectionspace.services.common.context.ServiceContext;
import org.collectionspace.services.common.document.BadRequestException;
import org.collectionspace.services.common.document.DocumentFilter;
import org.collectionspace.services.common.document.DocumentNotFoundException;
import org.collectionspace.services.common.document.DocumentWrapper;
import org.collectionspace.services.common.document.JaxbUtils;
import org.collectionspace.services.common.security.SecurityUtils;
import org.collectionspace.services.common.storage.TransactionContext;
import org.collectionspace.services.common.storage.jpa.JpaDocumentHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document handler for Role
 * @author 
 */
@SuppressWarnings("unchecked")
public class RoleDocumentHandler
		extends JpaDocumentHandler<Role, RolesList, Role, List<Role>> {
    private final Logger logger = LoggerFactory.getLogger(RoleDocumentHandler.class);
    private Role role;
    private RolesList rolesList;

    @Override
    public void handleCreate(DocumentWrapper<Role> wrapDoc) throws Exception {
        String id = UUID.randomUUID().toString();
        Role role = wrapDoc.getWrappedObject();
        
        // Synthesize the display name if it was not passed in.
        String displayName = role.getDisplayName();
        boolean displayNameEmpty = true;
        if (displayName != null) {
        	displayNameEmpty = displayName.trim().isEmpty();    	
        }
        if (displayNameEmpty == true) {
        	role.setDisplayName(role.getRoleName());
        }
        
        setTenant(role);
        role.setRoleName(RoleClient.getBackendRoleName(role.getRoleName(), role.getTenantId()));
        role.setCsid(id);
        // We do not allow creation of locked roles through the services.
        role.setMetadataProtection(null);
        role.setPermsProtection(null);        
    }
    
    @SuppressWarnings("rawtypes")
	@Override
	public void handleUpdate(DocumentWrapper<Role> wrapDoc) throws Exception {
		Role roleFound = wrapDoc.getWrappedObject();
		Role roleReceived = getCommonPart();
		// If marked as metadata immutable, do not do update
		if (!RoleClient.IMMUTABLE.equals(roleFound.getMetadataProtection())) {
			roleReceived
					.setRoleName(RoleClient.getBackendRoleName(roleReceived.getRoleName(), roleFound.getTenantId()));
			merge(roleReceived, roleFound);
		}
		//
		// Update perms is supplied.
		//
		ServiceContext ctx = this.getServiceContext();
		List<PermissionValue> permValueList = roleReceived.getPermission();
		if (permValueList != null && permValueList.size() > 0) {
            PermissionRoleSubResource subResource =
                    new PermissionRoleSubResource(PermissionRoleSubResource.ROLE_PERMROLE_SERVICE);
            //
            // First, delete the existing permroles (if any)
            //
            try {
            	subResource.deletePermissionRole(ctx, roleFound.getCsid(), SubjectType.PERMISSION);
            } catch (DocumentNotFoundException dnf) {
            	// Catch and ignore.  Just means the role has no existing relationships
            }
            //
            // Next, create the new permroles
            //
    		RoleValue roleValue = RoleFactory.createRoleValueInstance(roleFound);
    		PermissionRole permRole = PermissionRoleFactory.createPermissionRoleInstance(SubjectType.PERMISSION, roleValue,
    				permValueList, true, true);            
            subResource.createPermissionRole(ctx, permRole, SubjectType.PERMISSION);
            //
            // Finally, set the updated perm list in the result
            //
            PermissionRole newPermRole = subResource.getPermissionRole(ctx, roleFound.getCsid(), SubjectType.PERMISSION);
            roleFound.setPermission(newPermRole.getPermission());
		}
	}

    /**
     * Merge fields manually from 'from' to the 'to' role
     * -this method is created due to inefficiency of JPA EM merge
     * @param from
     * @param to
     * @return merged role
     */
    private Role merge(Role from, Role to) throws Exception {
        // A role's name cannot be changed
        if (!(from.getRoleName().equalsIgnoreCase(to.getRoleName()))) {
            String msg = "Role name cannot be changed " + to.getRoleName();
            logger.error(msg);
            throw new BadRequestException(msg);
        }
        
        if (from.getDisplayName() != null && !from.getDisplayName().trim().isEmpty() ) {
        	to.setDisplayName(from.getDisplayName());
        }
        if (from.getRoleGroup() != null && !from.getRoleGroup().trim().isEmpty()) {
            to.setRoleGroup(from.getRoleGroup());
        }
        if (from.getDescription() != null && !from.getDescription().trim().isEmpty()) {
            to.setDescription(from.getDescription());
        }

        if (logger.isDebugEnabled()) {
        	org.collectionspace.services.authorization.ObjectFactory objectFactory =
        		new org.collectionspace.services.authorization.ObjectFactory();
            logger.debug("Merged role on update=" + JaxbUtils.toString(objectFactory.createRole(to), Role.class));
        }
        
        return to;
    }
    
    @Override
    public void completeCreate(DocumentWrapper<Role> wrapDoc) throws Exception {
        Role role = wrapDoc.getWrappedObject();
        //
        // If there are perms in the payload, create the required role/perm relationships.
        //
    	List<PermissionValue> permValueList = role.getPermission();
    	if (permValueList != null && permValueList.size() > 0) {
    		//
    		// To prevent new Permissions being created (especially low-level Spring Security perms), we'll first flush the current
    		// JPA context to ensure our Role can be successfully persisted.
    		//
    		TransactionContext jpaTransactionContext = this.getServiceContext().getCurrentTransactionContext();
    		jpaTransactionContext.flush();
    		
    		// create and persist a permrole instance
    		// The caller of this method needs to ensure a valid and active EM (EntityManager) instance is in the Service context
    		RoleValue roleValue = RoleFactory.createRoleValueInstance(role);
    		PermissionRole permRole = PermissionRoleFactory.createPermissionRoleInstance(SubjectType.PERMISSION, roleValue,
    				permValueList, true, true);
            PermissionRoleSubResource subResource =
                    new PermissionRoleSubResource(PermissionRoleSubResource.ROLE_PERMROLE_SERVICE);
            subResource.createPermissionRole(getServiceContext(), permRole, SubjectType.PERMISSION);
    	}

    }

    @Override
    public void completeUpdate(DocumentWrapper<Role> wrapDoc) throws Exception {
        Role updatedRole = wrapDoc.getWrappedObject();
        getServiceContext().setOutput(updatedRole);
        sanitize(updatedRole);
    }

    @Override
    public void handleGet(DocumentWrapper<Role> wrapDoc) throws Exception {
        setCommonPart(extractCommonPart(wrapDoc));
        sanitize(getCommonPart());
        getServiceContext().setOutput(role);
    }

    @Override
    public void handleGetAll(DocumentWrapper<List<Role>> wrapDoc) throws Exception {
        RolesList rolesList = extractCommonPartList(wrapDoc);
        setCommonPartList(rolesList);
        getServiceContext().setOutput(getCommonPartList());
    }

	@Override
    public Role extractCommonPart(
            DocumentWrapper<Role> wrapDoc)
            throws Exception {
        Role role = wrapDoc.getWrappedObject();
        
        String includePermsQueryParamValue = (String) getServiceContext().getQueryParams().getFirst(RoleClient.INCLUDE_PERMS_QP);
        boolean includePerms = Tools.isTrue(includePermsQueryParamValue);
        if (includePerms) {
	        PermissionRoleSubResource permRoleResource =
	                new PermissionRoleSubResource(PermissionRoleSubResource.ROLE_PERMROLE_SERVICE);
	        PermissionRole permRole = permRoleResource.getPermissionRole(getServiceContext(), role.getCsid(), SubjectType.PERMISSION);
	        role.setPermission(permRole.getPermission());
        }
    
        return role;
    }

    @Override
    public void fillCommonPart(Role obj, DocumentWrapper<Role> wrapDoc)
            throws Exception {
        throw new UnsupportedOperationException("operation not relevant for AccountDocumentHandler");
    }

    /*
     * See https://issues.collectionspace.org/browse/DRYD-181
     * 
     * For backward compatibility, we could not change the role list to be a child class of AbstractCommonList.  This
     * would have change the result payload and would break existing API clients.  So the best we can do, it treat
     * the role list payload as a special case and return the paging information.
     * 
     */
	protected RolesList extractPagingInfoForRoles(RolesList roleList, DocumentWrapper<List<Role>> wrapDoc)
            throws Exception {

        DocumentFilter docFilter = this.getDocumentFilter();
        long pageSize = docFilter.getPageSize();
        long pageNum = pageSize != 0 ? docFilter.getOffset() / pageSize : pageSize;
        // set the page size and page number
        roleList.setPageNum(pageNum);
        roleList.setPageSize(pageSize);
        List<Role> docList = wrapDoc.getWrappedObject();
        // Set num of items in list. this is useful to our testing framework.
        roleList.setItemsInPage(docList.size());
        // set the total result size
        roleList.setTotalItems(docFilter.getTotalItemsResult());

        return roleList;
    }
	
    @Override
    public RolesList extractCommonPartList(
            DocumentWrapper<List<Role>> wrapDoc) throws Exception {

        RolesList rolesList = extractPagingInfoForRoles(new RolesList(), wrapDoc);        
        List<Role> list = new ArrayList<Role>();
        rolesList.setRole(list);
        for (Role role : wrapDoc.getWrappedObject()) {
            sanitize(role);
            list.add(role);
        }
        
        return rolesList;
    }

    @Override
    public Role getCommonPart() {
        return role;
    }

    @Override
    public void setCommonPart(Role role) {
        this.role = role;
    }

    @Override
    public RolesList getCommonPartList() {
        return rolesList;
    }

    @Override
    public void setCommonPartList(RolesList rolesList) {
        this.rolesList = rolesList;
    }

    @Override
    public String getQProperty(
            String prop) {
        return null;
    }

    @Override
    public DocumentFilter createDocumentFilter() {
        DocumentFilter filter = new RoleJpaFilter(this.getServiceContext());
        return filter;
    }

    /**
     * sanitize removes data not needed to be sent to the consumer
     * @param roleFound
     */
    @Override
    public void sanitize(Role role) {
        if (!SecurityUtils.isCSpaceAdmin()) {
            // role.setTenantId(null); // REM - There's no reason for hiding the tenant ID is there?
        }
    }

    private void setTenant(Role role) {
        //set tenant only if not available from input
        if (role.getTenantId() == null || role.getTenantId().isEmpty()) {
            role.setTenantId(getServiceContext().getTenantId());
        }
    }
}
