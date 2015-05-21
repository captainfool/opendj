/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable.spi;

/**
 * Runtime exception for storage supporting single access only.
 */
@SuppressWarnings("serial")
public final class StorageInUseException extends StorageRuntimeException
{

  /**
   * Constructor with a message and a cause.
   *
   * @param message
   *          the exception message
   * @param cause
   *          the cause of the exception
   */
  public StorageInUseException(String message, Throwable cause)
  {
    super(message, cause);
  }

  /**
   * Constructor with a message.
   *
   * @param message
   *          the exception message
   */
  public StorageInUseException(String message)
  {
    super(message);
  }

  /**
   * Constructor with a cause.
   *
   * @param cause
   *          the cause of the exception
   */
  public StorageInUseException(Throwable cause)
  {
    super(cause);
  }

}
