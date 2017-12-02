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

package org.collectionspace.services.common.document;

public class DocumentWrapperImpl<T> implements DocumentWrapper<T>{

    private T wrappedObject;

    public DocumentWrapperImpl(T obj) {
        this.wrappedObject = obj;
    }

    @Override
	public T getWrappedObject() {
        return wrappedObject;
    }

	@Override
	public T resetWrapperObject(T newObject) {
		wrappedObject = newObject;
		return wrappedObject;
	}
}