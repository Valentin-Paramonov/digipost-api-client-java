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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "sender-organization", propOrder = {
		"organizationId",
		"partId"
})
public class SenderOrganization {
	@XmlElement(name = "organization-id", nillable = false)
	protected String organizationId;
	@XmlElement(name = "part-id")
	protected String partId;

	public SenderOrganization() {
	}

	public SenderOrganization(final String organizationId, final String partId) {
		this.organizationId = organizationId;
		this.partId = partId;
	}

	public String getOrganizationId() {
		return organizationId;
	}

	public String getPartId() {
		return partId;
	}
}
