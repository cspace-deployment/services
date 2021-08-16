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
 *  
 *  $LastChangedRevision$
 */
package org.collectionspace.services.collectionobject;

import org.collectionspace.services.client.CollectionObjectClient;
import org.collectionspace.services.client.IQueryManager;
import org.collectionspace.services.client.PoxPayloadIn;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.Profiler;
import org.collectionspace.services.common.CSWebApplicationException;
import org.collectionspace.services.common.NuxeoBasedResource;
import org.collectionspace.services.common.ServiceMessages;
import org.collectionspace.services.common.context.ServiceContext;
import org.collectionspace.services.common.document.DocumentException;
import org.collectionspace.services.common.document.DocumentHandler;
import org.collectionspace.services.common.storage.JDBCTools;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.collectionspace.services.nuxeo.client.java.NuxeoDocumentException;
import org.collectionspace.services.relation.RelationsCommonList;
import org.collectionspace.services.relation.RelationshipType;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;


@Path(CollectionObjectClient.SERVICE_PATH_COMPONENT)
@Consumes("application/xml")
@Produces("application/xml")
public class CollectionObjectResource extends NuxeoBasedResource {
    
    final Logger logger = LoggerFactory.getLogger(CollectionObjectResource.class);

    @Override
    public String getVersionString() {
        final String lastChangeRevision = "$LastChangedRevision$";
        return lastChangeRevision;
    }

    @Override
    public String getServiceName() {
        return CollectionObjectClient.SERVICE_PATH_COMPONENT;
    }
    
    @Override
    public Class<CollectionobjectsCommon> getCommonPartClass() {
    	return CollectionobjectsCommon.class;
    }

    /**
     * Roundtrip.
     * 
     * This is an intentionally empty method used for getting a rough time estimate
     * of the overhead required for a client->server request/response cycle.
     * @param ms - milliseconds to delay
     * 
     * @return the response
     */
    @GET
    @Path("/{ms}/roundtrip")
    @Produces("application/xml")
    public Response roundtrip(
    		@PathParam("ms") String ms) {
    	Response result = null;
    	
    	Profiler profiler = new Profiler("roundtrip():", 1);
    	profiler.start();
		result = Response.status(HttpResponseCodes.SC_OK).build();
		profiler.stop();
		
		return result;
    }

    Throwable isUniuqeConstraintVilotation(NuxeoDocumentException e) {
    	Throwable result = null;
    	
    	Throwable cause = e.getCause();    	
        if (cause instanceof ConcurrentUpdateException) {
        	cause = cause.getCause();
            if (cause != null && cause instanceof org.postgresql.util.PSQLException) {
            	org.postgresql.util.PSQLException pe = (PSQLException) cause;
            	if ( pe.getSQLState() != null &&
            			pe.getSQLState().equals(JDBCTools.POSTGRES_UNIQUE_VIOLATION)) {
                    result = cause;
            	}
            }
        }

    	return result;
    }

    Throwable isUniuqeConstraintVilotation(Exception e) {
    	Throwable result = null;
    	
    	Throwable cause = e.getCause();
    	if (cause != null && cause instanceof NuxeoDocumentException) {
    		cause = cause.getCause();
            if (cause instanceof ConcurrentUpdateException) {
            	cause = cause.getCause();
	            if (cause != null && cause instanceof org.postgresql.util.PSQLException) {
	            	org.postgresql.util.PSQLException pe = (PSQLException) cause;
	            	if ( pe.getSQLState() != null &&
	            			pe.getSQLState().equals(JDBCTools.POSTGRES_UNIQUE_VIOLATION)) {
	                    result = cause;
	            	}
	            }
            }
    	}

    	return result;
    }

    /**
     * This override provides better error messaging in cases where the tenant's CollectionObject records have DB uniqueness constraints on
     * some fields.
     */
    @Override
    protected PoxPayloadOut update(String csid,
            PoxPayloadIn theUpdate,
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx)
            throws Exception {

    	try {
    		return super.update(csid, theUpdate, ctx);
        } catch (NuxeoDocumentException e) {
        	Throwable cause = isUniuqeConstraintVilotation(e);
        	if (cause != null) {
                throw bigReThrow(cause, ServiceMessages.UPDATE_FAILED);
        	}
            throw bigReThrow(e, ServiceMessages.UPDATE_FAILED);
        }
    }

    /**
     * This override provides better error messaging in cases where the tenant's CollectionObject records have DB uniqueness constraints on
     * some fields.
     */
    @Override
    protected Response create(PoxPayloadIn input, ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx) {
        try {
            return super.create(input, ctx);
        } catch (CSWebApplicationException e) {
        	Throwable cause = isUniuqeConstraintVilotation(e);
        	if (cause != null) {
                throw bigReThrow(cause, ServiceMessages.CREATE_FAILED);
        	}
            throw bigReThrow(e, ServiceMessages.CREATE_FAILED);
        }
    }
}
