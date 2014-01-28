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

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import no.digipost.api.client.representations.xml.DateTimeXmlAdapter;

import org.joda.time.DateTime;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "message-delivery", propOrder = { "messageId", "deliveryMethod", "status", "deliveredDate", "primaryDocument",
		"attachments", "links" })
@XmlRootElement(name = "message-delivery")
public class MessageDelivery extends Representation {

	@XmlElement(name = "message-id")
	protected String messageId;
	@XmlElement(name = "delivery-method", required = true)
	protected DeliveryMethod deliveryMethod;
	@XmlElement(required = true)
	protected MessageStatus status;
	@XmlElement(name = "delivered-date", type = String.class, nillable = false)
	@XmlJavaTypeAdapter(DateTimeXmlAdapter.class)
	@XmlSchemaType(name = "dateTime")
	protected DateTime deliveredDate;
	@XmlElement(name = "primary-document", required = true)
	protected Document primaryDocument;
	@XmlElement(name = "document")
	protected List<Document> attachments;

	public MessageDelivery() {
	}

	public MessageDelivery(final String messageId, final DeliveryMethod deliveryMethod, final MessageStatus status,
			final DateTime deliveredDate) {
		this.messageId = messageId;
		this.deliveryMethod = deliveryMethod;
		this.status = status;
		this.deliveredDate = deliveredDate;
	}

	@XmlElement(name = "link")
	protected List<Link> getLinks() {
		return links;
	}

	protected void setLinks(final List<Link> links) {
		this.links = links;
	}

	public String getMessageId() {
		return messageId;
	}

	public List<Document> getAttachments() {
		return attachments;
	}

	public boolean isSameMessageAs(final Message message) {
		return messageId.equals(message.getMessageId());
	}

	public MessageStatus getStatus() {
		return status;
	}

	public Link getEncryptionKeyLink() {
		return getLinkByRelationName(Relation.GET_ENCRYPTION_KEY);
	}

	public Link getSendLink() {
		return getLinkByRelationName(Relation.SEND);
	}

	public boolean willBeDeliveredInDigipost() {
		return DeliveryMethod.DIGIPOST.equals(deliveryMethod);
	}

	public boolean isAlreadyDeliveredToDigipost() {
		return DeliveryMethod.DIGIPOST.equals(deliveryMethod) && deliveredDate != null;
	}

	public Link getSelfLink() {
		return getLinkByRelationName(Relation.SELF);
	}

	public DeliveryMethod getDeliveryMethod() {
		return deliveryMethod;
	}

	public DateTime getDeliveredDate() {
		return deliveredDate;
	}

	public Document getPrimaryDocument() {
		return primaryDocument;
	}
}
