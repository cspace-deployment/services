/*
 * (C) Copyright 2006-2009 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

/*
 * An example Nuxeo event listener.
 */

package org.collectionspace.ecm.platform.quote.listener;

import java.util.List;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.collectionspace.ecm.platform.quote.service.QuoteServiceConfig;
import org.nuxeo.ecm.platform.relations.api.QNameResource;
import org.nuxeo.ecm.platform.relations.api.RelationManager;
import org.nuxeo.ecm.platform.relations.api.Resource;
import org.nuxeo.ecm.platform.relations.api.Statement;
import org.nuxeo.ecm.platform.relations.api.impl.StatementImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentRemovedQuoteEventListener extends
        AbstractQuoteListener implements PostCommitEventListener {

    private static final Logger logger = LoggerFactory.getLogger(DocumentRemovedQuoteEventListener.class);

    @Override
    protected void doProcess(CoreSession coreSession,
            RelationManager relationManager, QuoteServiceConfig config,
            DocumentModel docMessage) throws Exception {
        log.debug("Processing relations cleanup on Document removal");
        onDocumentRemoved(coreSession, relationManager, config, docMessage);
    }

    private static void onDocumentRemoved(CoreSession coreSession,
            RelationManager relationManager, QuoteServiceConfig config,
            DocumentModel docMessage) throws ClientException {

        Resource documentRes = relationManager.getResource(
                config.documentNamespace, docMessage, null);
        if (documentRes == null) {
            log.error("Could not adapt document model to relation resource ; "
                    + "check the service relation adapters configuration");
            return;
        }
        Statement pattern = new StatementImpl(null, null, documentRes);
        List<Statement> statementList = relationManager.getStatements(
                config.graphName, pattern);

        // remove comments
        for (Statement stmt : statementList) {
            QNameResource resource = (QNameResource) stmt.getSubject();
            String commentId = resource.getLocalName();
            DocumentModel docModel = (DocumentModel) relationManager.getResourceRepresentation(
                    config.commentNamespace, resource, null);

            if (docModel != null) {
                try {
                    coreSession.removeDocument(docModel.getRef());
                    log.debug("quote removal succeded for id: " + commentId);
                } catch (Exception e) {
                    log.error("quote removal failed", e);
                }
            } else {
                log.warn("quote/comment not found: id=" + commentId);
            }
        }
        coreSession.save();
        // remove relations
        relationManager.remove(config.graphName, statementList);
    }

}
