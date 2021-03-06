/*
 * (C) Copyright 2022 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     vdutat
 */
package com.acme.nuxeo.fsexporter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.collections.api.CollectionConstants;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.core.CoreQueryPageProviderDescriptor;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.io.fsexporter.DefaultExporterPlugin;
import org.nuxeo.runtime.api.Framework;

/**
 * 
 */
public class MainBlobExporterPlugin extends DefaultExporterPlugin {

    private static final Log log = LogFactory.getLog(MainBlobExporterPlugin.class);

    @Override
    public DocumentModelList getChildren(CoreSession session, DocumentModel doc, String customQuery) {
        Map<String, Serializable> props = new HashMap<>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
        String query;
        // if the user gives a query, we build a new Page Provider with the query provided
        if (StringUtils.isNotBlank(customQuery)) {
            if (customQuery.toLowerCase().contains(" where")) {
                query = customQuery + " AND " + NXQL.ECM_ANCESTORID + " = ?";
            } else {
                query = customQuery + " where " + NXQL.ECM_ANCESTORID + " = ?";
            }
        } else {
            query = "SELECT * FROM Document WHERE " + NXQL.ECM_ANCESTORID + " = ? AND ecm:mixinType !='HiddenInNavigation' AND ecm:isVersion = 0 AND ecm:isTrashed = 0";
        }
        if (log.isDebugEnabled()) {
            log.debug("<getChildren> " + doc.getPathAsString() + " " + query);
        }
        CoreQueryPageProviderDescriptor desc = new CoreQueryPageProviderDescriptor();
        desc.setPattern(query);
        PageProviderService ppService = Framework.getService(PageProviderService.class);
        @SuppressWarnings("unchecked")
        PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) ppService.getPageProvider("customPP", desc, null,
                null, null, null, props, new Object[] { doc.getId() });
        int countPages = 1;
        // get all the documents of the first page
        // TODO this fails when page provider is switched to elasticsearch
        DocumentModelList children = new DocumentModelListImpl(pp.getCurrentPage());
        // if there is more than one page, get the children of all the other pages and put into one list
        if (pp.getNumberOfPages() > 1) {
            while (countPages < pp.getNumberOfPages()) {
                pp.nextPage();
                List<DocumentModel> childrenTemp = pp.getCurrentPage();
                children.addAll(childrenTemp);
                countPages++;
            }
        }
        // return the complete list of documents
        if (log.isDebugEnabled()) {
            log.debug("<getChildren> " + children.size());
        }
        return children;
    }

    @Override
    public File serialize(CoreSession session, DocumentModel docfrom, String fsPath) throws IOException {
        File folder;
        File newFolder = null;
        
        if (log.isDebugEnabled()) {
            log.debug("<serialize> " + fsPath + " " + docfrom.getPathAsString());
        }
        folder = new File(fsPath);
        // if target directory doesn't exist, create it
        if (!folder.exists()) {
            folder.mkdir();
        }
        if ("/".equals(docfrom.getPathAsString())) {
            // we do not serialize the root document
            return folder;
        }
        if (docfrom.hasFacet("Folderish") || docfrom.hasFacet(CollectionConstants.COLLECTION_FACET)) {
            newFolder = new File(fsPath + "/" + docfrom.getName());
            newFolder.mkdir();
        }
        // get all the blobs of the blob holder
        BlobHolder myblobholder = docfrom.getAdapter(BlobHolder.class);
        if (myblobholder != null) {
            Blob blob = myblobholder.getBlob();
            if (blob != null) {
                // call the method to determine the name of the exported file
                String FileNameToExport = getFileName(blob, docfrom, folder, 1);
                // export the file to the target file system
                File target = new File(folder, FileNameToExport);
                blob.transferTo(target);
            }
        }
        if (newFolder != null) {
            folder = newFolder;
        }
        return folder;
    }

}
