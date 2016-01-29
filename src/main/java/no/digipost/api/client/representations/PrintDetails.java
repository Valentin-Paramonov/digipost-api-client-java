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
@XmlType(name = "print-details", propOrder = {
    "recipient",
    "returnAddress",
    "postType",
	"printColors",
	"nondeliverableHandling"
})
public class PrintDetails {

	public static enum PostType { A, B }
	public static enum NondeliverableHandling {	RETURN_TO_SENDER, SHRED }
	public static enum PrintColors { MONOCHROME, COLORS; }


    @XmlElement(required = true)
    protected PrintRecipient recipient;
    @XmlElement(name = "return-address", required = true)
    protected PrintRecipient returnAddress;
    @XmlElement(name = "post-type", required = true)
    protected PostType postType;
	@XmlElement(name = "color")
	protected PrintColors printColors;
	@XmlElement(name ="nondeliverable-handling")
	protected NondeliverableHandling nondeliverableHandling;


	PrintDetails() {}

	public PrintDetails(final PrintRecipient recipient, final PrintRecipient returnAddress, final PostType postType) {
		this(recipient, returnAddress, postType, null, null);
	}

	public PrintDetails(final PrintRecipient recipient, final PrintRecipient returnAddress, final PostType postType, final PrintColors colors, final NondeliverableHandling nondeliverableHandling) {
		this.recipient = recipient;
		this.returnAddress = returnAddress;
		this.postType = postType;
		this.printColors = colors;
		this.nondeliverableHandling = nondeliverableHandling;
	}

	public PrintRecipient getRecipient() {
		return recipient;
	}

	public PrintRecipient getReturnAddress() {
		return returnAddress;
	}

	public PostType getPostType() {
		return postType;
	}

	public PrintColors getPrintColors() {
		return printColors;
	}

	public NondeliverableHandling getNondeliverableHandling() {
		return nondeliverableHandling;
	}
}