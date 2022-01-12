package com.acme.nuxeo.fsexporter;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.scroll.Scroll;
import org.nuxeo.ecm.core.api.scroll.ScrollRequest;
import org.nuxeo.ecm.core.api.scroll.ScrollService;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.scroll.DocumentScrollRequest;
import org.nuxeo.runtime.api.Framework;

public class MainBlobElasticsearchExporterPlugin extends MainBlobExporterPlugin {

    private static final Log log = LogFactory.getLog(MainBlobElasticsearchExporterPlugin.class);

    @Override
    public DocumentModelList getChildren(CoreSession session, DocumentModel doc, String customQuery) {
        String query;
        // if the user gives a query, we build a new query with the query provided
        String criteria = NXQL.ECM_ANCESTORID + " = " + NXQL.escapeString(doc.getId());
        if (StringUtils.isNotBlank(customQuery)) {
            if (customQuery.toLowerCase().contains(" where")) {
                query = customQuery + " AND " + criteria;
            } else {
                query = customQuery + " where " + criteria;
            }
        } else {
            query = "SELECT * FROM Document WHERE " + criteria + " AND ecm:mixinType !='HiddenInNavigation' AND ecm:isVersion = 0 AND ecm:isTrashed = 0";
        }
        if (log.isDebugEnabled()) {
            log.debug("<getChildren> " + doc.getPathAsString() + " " + query);
        }
        ScrollService scrollService = Framework.getService(ScrollService.class);
        ScrollRequest request = DocumentScrollRequest.builder(query)
                .name("elastic")
                .username(session.getPrincipal().getName())
                .build();
        DocumentModelList children = new DocumentModelListImpl();
        try (Scroll scroll = scrollService.scroll(request)) {
            while (scroll.hasNext()) {
                List<String> docIds = scroll.next();
                children.addAll(docIds.stream().map(IdRef::new).map(session::getDocument).collect(Collectors.toList()));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("<getChildren> " + children.size());
        }
        return children;
    }

}
