/**
 * Copyright (C) Posten Norge AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.digipost.api.client.representations;

import no.digipost.api.client.representations.xml.DateTimeXmlAdapter;
import org.joda.time.DateTime;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "event")
public class DocumentEvent {
	@XmlAttribute(name = "document", required = true)
	private String document;
	@XmlAttribute(name = "type", required = true)
	private DocumentEventType type;
	@XmlAttribute(name = "created", required = true)
	@XmlJavaTypeAdapter(DateTimeXmlAdapter.class)
	@XmlSchemaType(name = "dateTime")
	private DateTime created;
	@XmlAttribute(name = "description")
	private String description;

	public DocumentEvent() {
	}

	public DocumentEvent(String document, DocumentEventType type, DateTime created, String description) {
		this.document = document;
		this.type = type;
		this.created = created;
		this.description = description;
	}

	public String getDocument() {
		return document;
	}

	public DocumentEventType getType() {
		return type;
	}

	public DateTime getCreated() {
		return created;
	}

	public String getDescription() {
		return description;
	}
}