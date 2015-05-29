/*
 * Copyright (c) 1996-2014 Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import javax.mail.*;
import javax.mail.event.*;
import javax.activation.*;

import com.sun.mail.imap.*;

/**
 * Use the IMAP IdleManager to monitor multiple folders for new messages.
 * The program prompts for the folder names.
 */
public class idlemonitor {

    static String protocol;
    static String host = null;
    static String user = null;
    static String password = null;
    static String url = null;
    static int port = -1;
    static boolean verbose = false;
    static boolean debug = false;

    public static void main(String argv[]) {
	int optind;
	InputStream msgStream = System.in;

	for (optind = 0; optind < argv.length; optind++) {
	    if (argv[optind].equals("-T")) {
		protocol = argv[++optind];
	    } else if (argv[optind].equals("-H")) {
		host = argv[++optind];
	    } else if (argv[optind].equals("-U")) {
		user = argv[++optind];
	    } else if (argv[optind].equals("-P")) {
		password = argv[++optind];
	    } else if (argv[optind].equals("-v")) {
		verbose = true;
	    } else if (argv[optind].equals("-D")) {
		debug = true;
	    } else if (argv[optind].equals("-L")) {
		url = argv[++optind];
	    } else if (argv[optind].equals("-p")) {
		port = Integer.parseInt(argv[++optind]);
	    } else if (argv[optind].equals("--")) {
		optind++;
		break;
	    } else if (argv[optind].startsWith("-")) {
		System.out.println(
"Usage: idlemonitor [-L url] [-T protocol] [-H host] [-p port] [-U user]");
		System.out.println(
"\t[-P password] [-v] [-D]");
		System.exit(1);
	    } else {
		break;
	    }
	}

	try {
	    ExecutorService es = Executors.newCachedThreadPool();

	    // Get a Properties object
	    Properties props = System.getProperties();
	    props.setProperty("mail.imap.usesocketchannels", "true");
	    props.setProperty("mail.imaps.usesocketchannels", "true");
	    props.setProperty("mail.event.scope", "application");
	    props.put("mail.event.executor", es);

	    // Get a Session object
	    Session session = Session.getInstance(props, null);
	    session.setDebug(debug);

	    final IdleManager im = new IdleManager(session, es);

	    // Get a Store object
	    Store store = null;
	    if (url != null) {
		URLName urln = new URLName(url);
		store = session.getStore(urln);
		store.connect();
	    } else {
		if (protocol != null)		
		    store = session.getStore(protocol);
		else
		    store = session.getStore();

		// Connect
		if (host != null || user != null || password != null)
		    store.connect(host, port, user, password);
		else
		    store.connect();
	    }

	    List<Folder> folders = new ArrayList<Folder>();
	    BufferedReader in =
		new BufferedReader(new InputStreamReader(System.in));
	    for (;;) {
		System.out.print("Folder to monitor: ");
		System.out.flush();
		String line = in.readLine();
		if (line == null)
		    break;

		if (line.startsWith("#")) {
		    int fn = Integer.parseInt(line.substring(1));
		    Folder f = folders.get(fn);
		    System.out.println("Folder " + f.getName() +
					" isOpen " + f.isOpen());
		    im.watch(f);
		    continue;
		}

		// Open a Folder
		Folder folder = store.getFolder(line);
		if (folder == null || !folder.exists()) {
		    System.out.println("Invalid folder");
		    continue;
		}

		folder.open(Folder.READ_WRITE);
		folders.add(folder);
		// Add messageCountListener to listen for new messages
		folder.addMessageCountListener(new MessageCountAdapter() {
		    public void messagesAdded(MessageCountEvent ev) {
			Folder folder = (Folder)ev.getSource();
			Message[] msgs = ev.getMessages();
			System.out.println("Folder: " + folder +
			    " got " + msgs.length + " new messages");

			// Just dump out the new messages
			for (int i = 0; i < msgs.length; i++) {
			    try {
				System.out.println("-----");
				System.out.println("Message " +
				    msgs[i].getMessageNumber() + ":");
				msgs[i].writeTo(System.out);
				im.watch(folder);
			    } catch (IOException ioex) { 
				ioex.printStackTrace();	
			    } catch (MessagingException mex) {
				mex.printStackTrace();
			    }
			}
		    }
		});
		im.watch(folder);
	    }
	    im.stop();
	} catch (Exception ex) {
	    ex.printStackTrace();
	}
    }
}
