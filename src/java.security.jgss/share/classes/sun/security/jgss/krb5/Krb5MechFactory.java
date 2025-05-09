/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.jgss.krb5;

import org.ietf.jgss.*;
import sun.security.jgss.GSSUtil;
import sun.security.jgss.GSSCaller;
import sun.security.jgss.spi.*;
import java.security.Provider;
import java.util.Vector;

/**
 * Krb5 Mechanism plug in for JGSS
 * This is the properties object required by the JGSS framework.
 * All mechanism specific information is defined here.
 *
 * @author Mayank Upadhyay
 */

public final class Krb5MechFactory implements MechanismFactory {

    static final Provider PROVIDER =
        new sun.security.jgss.SunProvider();

    static final Oid GSS_KRB5_MECH_OID =
        createOid("1.2.840.113554.1.2.2");

    static final Oid NT_GSS_KRB5_PRINCIPAL =
        createOid("1.2.840.113554.1.2.2.1");

    private static final Oid[] nameTypes =
        new Oid[] { GSSName.NT_USER_NAME,
                        GSSName.NT_HOSTBASED_SERVICE,
                        GSSName.NT_EXPORT_NAME,
                        NT_GSS_KRB5_PRINCIPAL};

    private final GSSCaller caller;

    private static Krb5CredElement getCredFromSubject(GSSNameSpi name,
                                                      boolean initiate)
        throws GSSException {
        Vector<Krb5CredElement> creds =
            GSSUtil.searchSubject(name, GSS_KRB5_MECH_OID, initiate,
                                  (initiate ?
                                   Krb5InitCredential.class :
                                   Krb5AcceptCredential.class));

        return ((creds == null || creds.isEmpty()) ?
                null : creds.firstElement());
    }

    public Krb5MechFactory() {
        this(GSSCaller.CALLER_UNKNOWN);
    }

    public Krb5MechFactory(GSSCaller caller) {
        this.caller = caller;
    }

    public GSSNameSpi getNameElement(String nameStr, Oid nameType)
        throws GSSException {
        return Krb5NameElement.getInstance(nameStr, nameType);
    }

    public GSSNameSpi getNameElement(byte[] name, Oid nameType)
        throws GSSException {
        // At this point, even an exported name is stripped down to safe
        // bytes only
        // XXX Use encoding here
        return Krb5NameElement.getInstance(new String(name), nameType);
    }

    public GSSCredentialSpi getCredentialElement(GSSNameSpi name,
           int initLifetime, int acceptLifetime,
           int usage) throws GSSException {

        if (name != null && !(name instanceof Krb5NameElement)) {
            name = Krb5NameElement.getInstance(name.toString(),
                                       name.getStringNameType());
        }

        Krb5CredElement credElement = getCredFromSubject
            (name, (usage != GSSCredential.ACCEPT_ONLY));

        if (credElement == null) {
            if (usage == GSSCredential.INITIATE_ONLY ||
                usage == GSSCredential.INITIATE_AND_ACCEPT) {
                credElement = Krb5InitCredential.getInstance
                    (caller, (Krb5NameElement) name, initLifetime);
                credElement = Krb5ProxyCredential.tryImpersonation(
                        caller, (Krb5InitCredential)credElement);
            } else if (usage == GSSCredential.ACCEPT_ONLY) {
                credElement =
                    Krb5AcceptCredential.getInstance(caller,
                                                     (Krb5NameElement) name);
            } else
                throw new GSSException(GSSException.FAILURE, -1,
                                       "Unknown usage mode requested");
        }
        return credElement;
    }

    public GSSContextSpi getMechanismContext(GSSNameSpi peer,
                             GSSCredentialSpi myInitiatorCred, int lifetime)
        throws GSSException {
        if (peer != null && !(peer instanceof Krb5NameElement)) {
            peer = Krb5NameElement.getInstance(peer.toString(),
                                       peer.getStringNameType());
        }
        // XXX Convert myInitiatorCred to Krb5CredElement
        if (myInitiatorCred == null) {
            myInitiatorCred = getCredentialElement(null, lifetime, 0,
                GSSCredential.INITIATE_ONLY);
        }
        return new Krb5Context(caller, (Krb5NameElement)peer,
                               (Krb5CredElement)myInitiatorCred, lifetime);
    }

    public GSSContextSpi getMechanismContext(GSSCredentialSpi myAcceptorCred)
        throws GSSException {
        // XXX Convert myAcceptorCred to Krb5CredElement
        if (myAcceptorCred == null) {
            myAcceptorCred = getCredentialElement(null, 0,
                GSSCredential.INDEFINITE_LIFETIME, GSSCredential.ACCEPT_ONLY);
        }
        return new Krb5Context(caller, (Krb5CredElement)myAcceptorCred);
    }

    public GSSContextSpi getMechanismContext(byte[] exportedContext)
        throws GSSException {
        return new Krb5Context(caller, exportedContext);
    }


    public Oid getMechanismOid() {
        return GSS_KRB5_MECH_OID;
    }

    public Provider getProvider() {
        return PROVIDER;
    }

    public Oid[] getNameTypes() {
        // nameTypes is cloned in GSSManager.getNamesForMech
        return nameTypes;
    }

    private static Oid createOid(String oidStr) {
        Oid retVal = null;
        try {
            retVal = new Oid(oidStr);
        } catch (GSSException e) {
            // Should not happen!
        }
        return retVal;
    }
}
