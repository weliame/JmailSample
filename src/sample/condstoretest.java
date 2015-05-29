/*
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
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
import javax.mail.*;
import javax.mail.event.*;
import javax.mail.internet.*;

import com.sun.mail.imap.*;

/*
 * Test CONDSTORE FETCH and SEARCH.
 *
 * @author Bill Shannon
 */

public class condstoretest {

    static String protocol;
    static String host = null;
    static String user = null;
    static String password = null;
    static String mbox = null;
    static String url = null;
    static int port = -1;
    static boolean verbose = false;
    static boolean debug = false;
    static long modseq = -1;

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
	    } else if (argv[optind].equals("-f")) {
		mbox = argv[++optind];
	    } else if (argv[optind].equals("-L")) {
		url = argv[++optind];
	    } else if (argv[optind].equals("-p")) {
		port = Integer.parseInt(argv[++optind]);
	    } else if (argv[optind].equals("-M")) {
		modseq = Long.parseLong(argv[++optind]);
	    } else if (argv[optind].equals("--")) {
		optind++;
		break;
	    } else if (argv[optind].startsWith("-")) {
		System.out.println(
"Usage: msgshow [-L url] [-T protocol] [-H host] [-p port] [-U user]");
		System.out.println(
"\t[-P password] [-f mailbox] [msgnum ...] [-v] [-D] [-s] [-S] [-a]");
		System.out.println(
"or     msgshow -m [-v] [-D] [-s] [-S] [-f msg-file]");
		System.exit(1);
	    } else {
		break;
	    }
	}

	try {
	    // Get a Properties object
	    Properties props = System.getProperties();

	    // Get a Session object
	    Session session = Session.getInstance(props, null);
	    session.setDebug(debug);

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
	    

	    // Open the Folder

	    Folder folder = store.getDefaultFolder();
	    if (folder == null) {
		System.out.println("No default folder");
		System.exit(1);
	    }

	    if (mbox == null)
		mbox = "condstore";
	    folder = folder.getFolder(mbox);
	    if (folder == null) {
		System.out.println("Invalid folder");
		System.exit(1);
	    }

	    if (folder.exists())
		folder.delete(true);	// start fresh
	    folder.create(Folder.HOLDS_MESSAGES);
	    MimeMessage msg1 = new MimeMessage(session);
	    msg1.setFrom();
	    msg1.setSentDate(new Date());
	    msg1.setSubject("test 1");
	    msg1.setText("test\n");
	    msg1.saveChanges();
	    MimeMessage msg2 = new MimeMessage(session);
	    msg2.setFrom();
	    msg2.setSentDate(new Date());
	    msg2.setSubject("test 2");
	    msg2.setText("test\n");
	    msg2.saveChanges();
	    IMAPFolder ifolder = (IMAPFolder)folder;
	    AppendUID[] auid =
		ifolder.appendUIDMessages(new Message[] { msg1, msg2 });
	    long highestmodseq = ifolder.getHighestModSeq();
	    System.out.println("HIGHESTMODSEQ = " + highestmodseq);
	    UIDFolder uidfolder = (UIDFolder)folder;
	    System.out.println("UIDVALIDITY = " + auid[0].uidvalidity);
	    ResyncData rd = new ResyncData(
				auid[0].uidvalidity,
				(modseq != -1 ? modseq : highestmodseq)
				);

	    List<MailEvent> ev = ifolder.open(Folder.READ_WRITE, rd);
	    System.out.println(ev);
	    if (ev != null)
		for (MailEvent e : ev) {
		    System.out.println(e);
		    if (e instanceof MessageVanishedEvent) {
			MessageVanishedEvent mve = (MessageVanishedEvent)e;
			for (long uid : mve.getUIDs())
			    System.out.println("UID VANISHED: " + uid);
		    }
		}

	    folder.setFlags(1, 1, new Flags(Flags.Flag.SEEN), true);
	    folder.close(false);	// make sure no flags cached
	    folder.open(Folder.READ_WRITE);
	    Message[] msgs = ifolder.getMessagesByUIDChangedSince(
				1, UIDFolder.LASTUID, highestmodseq);
	    System.out.println("Changed: " + msgs.length);
	    for (int i = 0; i < msgs.length; i++) {
		Message msg = msgs[i];
		System.out.println("Number: " + msg.getMessageNumber());
		//System.out.println("Subject: " + msg.getSubject());
		System.out.println("Seen: " + msg.isSet(Flags.Flag.SEEN));
	    }

	    // this will find both messages since the test is >=
	    msgs = ifolder.search(new ModifiedSinceTerm(highestmodseq));
	    System.out.println("Search: " + msgs.length);
	    for (int i = 0; i < msgs.length; i++) {
		Message msg = msgs[i];
		System.out.println("Number: " + msg.getMessageNumber());
		//System.out.println("Subject: " + msg.getSubject());
		System.out.println("Seen: " + msg.isSet(Flags.Flag.SEEN));
	    }

	    folder.close(false);
	    store.close();
	} catch (Exception ex) {
	    System.out.println("Oops, got exception! " + ex.getMessage());
	    ex.printStackTrace();
	    System.exit(1);
	}
	System.exit(0);
    }
}
