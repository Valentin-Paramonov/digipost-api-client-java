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
package no.digipost.api.client.filters.request;

import static no.digipost.api.client.DigipostClient.NOOP_EVENT_LOGGER;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import no.digipost.api.client.EventLogger;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.crypto.ExtendedDigest;

import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

@Priority(Priorities.HEADER_DECORATOR)
public abstract class RequestContentHashFilter implements ClientRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(RequestContentHashFilter.class);
	private final EventLogger eventLogger;
	private final Class<? extends ExtendedDigest> digestClass;
	private final String header;

	public RequestContentHashFilter(final EventLogger eventListener, final Class<? extends ExtendedDigest> digestClass, final String header) {
		eventLogger = eventListener != null ? eventListener : NOOP_EVENT_LOGGER;
		this.digestClass = digestClass;
		this.header = header;
	}

	public RequestContentHashFilter(final Class<? extends ExtendedDigest> digestClass, final String header) {
		this(NOOP_EVENT_LOGGER, digestClass, header);
	}

	@Override
	public void filter(ClientRequestContext clientRequestContext) {
		clientRequestContext.setEntityStream(new ContentHashOutputStream(clientRequestContext, clientRequestContext.getEntityStream()));

	}

	private void log(final String stringToSignMsg) {
		LOG.debug(stringToSignMsg);
		eventLogger.log(stringToSignMsg);
	}

	private final class ContentHashOutputStream extends OutputStream {

		private final ByteArrayOutputStream byteArrayOutputStream;
		private final OutputStream jerseyStream;
		private final ClientRequestContext request;

		public ContentHashOutputStream(final ClientRequestContext request, final OutputStream jerseyStream) {
			this.jerseyStream = jerseyStream;
			this.request = request;

			byteArrayOutputStream = new ByteArrayOutputStream();
		}

		@Override
		public void write(final int b) throws IOException {
			byteArrayOutputStream.write(b);
		}

		@Override
		public void close() throws IOException {
			byteArrayOutputStream.close();
			byte[] byteArray = byteArrayOutputStream.toByteArray();
			settContentHashHeader(byteArray);
			IOUtils.write(byteArray, jerseyStream);
			jerseyStream.close();
		}

		private void settContentHashHeader(final byte[] data) {
			try {
				ExtendedDigest instance = digestClass.newInstance();
				byte[] result = new byte[instance.getDigestSize()];
				instance.update(data, 0, data.length);
				instance.doFinal(result, 0);
				String hash = new String(Base64.encode(result));
				request.getHeaders().add(header, hash);
				log(RequestContentHashFilter.class.getSimpleName() + " satt headeren " + header + "=" + hash);
			} catch (InstantiationException e) {
				log("Feil ved generering av " + header + " : " + e.getMessage());
			} catch (IllegalAccessException e) {
				log("Feil ved generering av " + header + " : " + e.getMessage());
			}
		}
	}
}