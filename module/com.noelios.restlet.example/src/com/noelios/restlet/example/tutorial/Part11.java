/*
 * Copyright 2005-2006 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.example.tutorial;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Directory;
import org.restlet.Guard;
import org.restlet.Restlet;
import org.restlet.Router;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;

/**
 * Routers and hierarchical URIs
 * 
 * @author Jerome Louvel (contact@noelios.com)
 */
public class Part11 implements Constants {
    public static void main(String[] args) throws Exception {
        // Create a component
        Component component = new Component();
        component.getServers().add(Protocol.HTTP, 8182);
        component.getClients().add(Protocol.FILE);

        // Create an application
        Application application = new Application(component.getContext()) {
            @Override
            public Restlet createRoot() {
                // Create a root Router
                Router router = new Router(getContext());

                // Attach a Guard to secure access to the chained directory
                // handler
                Guard guard = new Guard(getContext(),
                        ChallengeScheme.HTTP_BASIC, "Restlet tutorial");
                guard.getAuthorizations().put("scott", "tiger");
                router.attach("/docs/", guard);

                // Create a Directory able to return a deep hierarchy of Web
                // files
                Directory directory = new Directory(getContext(), ROOT_URI);
                guard.setNext(directory);

                // Create the Account Handler
                Restlet account = new Restlet() {
                    @Override
                    public void handle(Request request, Response response) {
                        // Print the requested URI path
                        String message = "Account of user \""
                                + request.getAttributes().get("user") + "\"";
                        response.setEntity(message, MediaType.TEXT_PLAIN);
                    }
                };

                // Create the Orders Handler
                Restlet orders = new Restlet(getContext()) {
                    @Override
                    public void handle(Request request, Response response) {
                        // Print the user name of the requested orders
                        String message = "Orders of user \""
                                + request.getAttributes().get("user") + "\"";
                        response.setEntity(message, MediaType.TEXT_PLAIN);
                    }
                };

                // Create the Order Handler
                Restlet order = new Restlet(getContext()) {
                    @Override
                    public void handle(Request request, Response response) {
                        // Print the user name of the requested orders
                        String message = "Order \""
                                + request.getAttributes().get("order")
                                + "\" for user \""
                                + request.getAttributes().get("user") + "\"";
                        response.setEntity(message, MediaType.TEXT_PLAIN);
                    }
                };

                // Attach the Handlers to the Router
                router.attach("/users/{user}", account);
                router.attach("/users/{user}/orders", orders);
                router.attach("/users/{user}/orders/{order}", order);

                // Return the root router
                return router;
            }
        };

        // Attach the application to the component and start it
        component.getDefaultHost().attach("", application);
        component.start();
    }

}
