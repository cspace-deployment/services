package org.collectionspace.services.common.storage.jpa;

import java.util.List;

import org.collectionspace.services.common.document.AbstractDocumentHandlerImpl;
import org.collectionspace.services.common.document.DocumentFilter;
import org.collectionspace.services.common.document.DocumentWrapper;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.collectionspace.services.lifecycle.Lifecycle;

public abstract class JpaDocumentHandler<T, TL, WT, WLT>
	extends AbstractDocumentHandlerImpl<T, TL, WT, WLT>{

    /**
     * Extract paging info.
     *
     * @param commonsList the commons list
     * @return the tL
     * @throws Exception the exception
     */
    public TL extractPagingInfo(TL theCommonList, DocumentWrapper<WLT> wrapDoc)
            throws Exception {
        AbstractCommonList commonList = (AbstractCommonList) theCommonList;

        DocumentFilter docFilter = this.getDocumentFilter();
        long pageSize = docFilter.getPageSize();
        long pageNum = pageSize != 0 ? docFilter.getOffset() / pageSize : pageSize;
        // set the page size and page number
        commonList.setPageNum(pageNum);
        commonList.setPageSize(pageSize);
        List docList = (List)wrapDoc.getWrappedObject();
        // Set num of items in list. this is useful to our testing framework.
        commonList.setItemsInPage(docList.size());
        // set the total result size
        commonList.setTotalItems(docList.size());

        return (TL) commonList;
    }
    
    public Lifecycle getLifecycle(String docTypeName) {
    	Lifecycle result = new Lifecycle();
    	result.setName("Life cycles are not supported by the JPA-based services.");
    	return result; // NOTE: As of 3/2012, none of the JPA-based services support a life cycle type.
    }
    
    @Override
    public Lifecycle getLifecycle() {
    	return getLifecycle(null); // NOTE: As of 3/2012, none of the JPA-based services support a life cycle type.
    }
}
