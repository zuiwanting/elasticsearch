/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.percolate;

import org.elasticsearch.ElasticSearchGenerationException;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.broadcast.BroadcastOperationRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.action.ValidateActions.addValidationError;

/**
 *
 */
public class PercolateRequest extends BroadcastOperationRequest<PercolateRequest> {

    public static final XContentType contentType = Requests.CONTENT_TYPE;

    private String documentType;
    private String routing;
    private String preference;

    private BytesReference source;
    private boolean unsafe;

    private BytesReference fetchedDoc;

    // Used internally in order to compute tookInMillis, TransportBroadcastOperationAction itself doesn't allow
    // to hold it temporarily in an easy way
    long startTime;

    PercolateRequest() {
    }

    public PercolateRequest(String index, String documentType) {
        super(new String[]{index});
        this.documentType = documentType;
    }

    public PercolateRequest(PercolateRequest request, BytesReference fetchedDoc) {
        super(request.indices());
        this.documentType = request.documentType();
        this.routing = request.routing();
        this.preference = request.preference();
        this.source = request.source;
        this.fetchedDoc = fetchedDoc;
    }

    public String documentType() {
        return documentType;
    }

    public void documentType(String type) {
        this.documentType = type;
    }

    public String routing() {
        return routing;
    }

    public PercolateRequest routing(String routing) {
        this.routing = routing;
        return this;
    }

    public String preference() {
        return preference;
    }

    public PercolateRequest preference(String preference) {
        this.preference = preference;
        return this;
    }

    /**
     * Before we fork on a local thread, make sure we copy over the bytes if they are unsafe
     */
    @Override
    public void beforeLocalFork() {
        if (unsafe) {
            source = source.copyBytesArray();
            unsafe = false;
        }
    }

    public BytesReference source() {
        return source;
    }

    public PercolateRequest source(Map document) throws ElasticSearchGenerationException {
        return source(document, contentType);
    }

    public PercolateRequest source(Map document, XContentType contentType) throws ElasticSearchGenerationException {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(contentType);
            builder.map(document);
            return source(builder);
        } catch (IOException e) {
            throw new ElasticSearchGenerationException("Failed to generate [" + document + "]", e);
        }
    }

    public PercolateRequest source(String document) {
        this.source = new BytesArray(document);
        this.unsafe = false;
        return this;
    }

    public PercolateRequest source(XContentBuilder documentBuilder) {
        source = documentBuilder.bytes();
        unsafe = false;
        return this;
    }

    public PercolateRequest source(byte[] document) {
        return source(document, 0, document.length);
    }

    public PercolateRequest source(byte[] source, int offset, int length) {
        return source(source, offset, length, false);
    }

    public PercolateRequest source(byte[] source, int offset, int length, boolean unsafe) {
        return source(new BytesArray(source, offset, length), unsafe);
    }

    public PercolateRequest source(BytesReference source, boolean unsafe) {
        this.source = source;
        this.unsafe = unsafe;
        return this;
    }

    public PercolateRequest source(PercolateSourceBuilder sourceBuilder) {
        this.source = sourceBuilder.buildAsBytes(contentType);
        this.unsafe = false;
        return this;
    }

    BytesReference fetchedDoc() {
        return fetchedDoc;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = super.validate();
        if (indices == null || indices.length == 0) {
            validationException = addValidationError("index is missing", validationException);
        }
        if (documentType == null) {
            validationException = addValidationError("type is missing", validationException);
        }
        if (source == null) {
            validationException = addValidationError("source is missing", validationException);
        }
        return validationException;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        documentType = in.readString();
        routing = in.readOptionalString();
        preference = in.readOptionalString();
        unsafe = false;
        source = in.readBytesReference();
        startTime = in.readVLong();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(documentType);
        out.writeOptionalString(routing);
        out.writeOptionalString(preference);
        out.writeBytesReference(source);
        out.writeVLong(startTime);
    }
}
