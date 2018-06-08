/*********************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.ppp4j.messages;

public class ErroneousParameter {
	public ParameterType parameterType;
	public String message;
	public String componentVersionId;

	public ErroneousParameter() {
	}

	public ErroneousParameter(ParameterType parameterType, String message, String componentVersionId) {
		this.parameterType = parameterType;
		this.message = message;
		this.componentVersionId = componentVersionId;
	}
}
