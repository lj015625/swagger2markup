/*
 * Copyright 2016 Robert Winkler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.swagger2markup.internal.type;

import io.github.swagger2markup.markup.builder.MarkupDocBuilder;

/**
 * Array type abstraction
 */
public class ArrayType extends Type {

    protected String collectionFormat;
    protected Type ofType;

    public ArrayType(String name, Type ofType) {
        this(name, ofType, null);
    }

    public ArrayType(String name, Type ofType, String collectionFormat) {
        super(name == null ? "array" : name);
        this.collectionFormat = collectionFormat;
        this.ofType = ofType;
    }

    @Override
    public String displaySchema(MarkupDocBuilder docBuilder) {
        String collectionFormat = "";
        if (this.collectionFormat != null)
            collectionFormat = this.collectionFormat + " ";
        return String.format("%s%s array", collectionFormat, ofType.displaySchema(docBuilder));
    }

    public String getCollectionFormat() {
        return collectionFormat;
    }

    public void setCollectionFormat(String collectionFormat) {
        this.collectionFormat = collectionFormat;
    }

    public Type getOfType() {
        return ofType;
    }

    public void setOfType(Type ofType) {
        this.ofType = ofType;
    }
}
