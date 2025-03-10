/**************************************************************************
 *  Copyright (C) 2013 Atlas of Living Australia
 *  All Rights Reserved.
 * 
 *  The contents of this file are subject to the Mozilla Public
 *  License Version 1.1 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of
 *  the License at http://www.mozilla.org/MPL/
 * 
 *  Software distributed under the License is distributed on an "AS
 *  IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.biocache.service;

import java.util.Date;
import java.util.Properties;

import javax.annotation.PostConstruct;

import jakarta.mail.Transport;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * A service responsible for sending emails.
 * 
 * @author Natasha Carter (natasha.carter@csiro.au)
 */
@Component("emailService")
public class EmailService {
	
    /** The default sender for emails from the biocache */
    @Value("${email.sender:support@ala.org.au}")
    private String sender;
    private static final Logger logger = Logger.getLogger(EmailService.class);
    private Properties properties = new Properties();
    @Value("${mail.smtp.host:localhost}")
    private String host;
    @Value("${mail.smtp.port:25}")
    private String port;
    @Value("${mail.smtp.starttls.enable:false}")
    private boolean starttls;
    @Value("${mail.smtp.username:}")
    private String username;
    @Value("${mail.smtp.password:}")
    private String password;

    @PostConstruct
    protected void init(){        
        properties.put("mail.smtp.host", host);        
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.starttls.enable", starttls);
    }
    
    /**
     * Sends an email with the supplied details. 
     * 
     * @param recipient
     * @param copy
     * @param subject
     * @param content
     * @param sender
     */
    public void sendEmail(String recipient, String copy, String subject, String content, String sender){

        logger.debug("Send email to : " + recipient);
//        logger.debug("Body: " + content);
        Session session = Session.getDefaultInstance(properties);
        
        try {

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender));
            message.setSentDate(new Date());
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            if(copy != null){
                message.addRecipient(Message.RecipientType.CC, new InternetAddress(copy));
            }
            message.setSubject(subject);
            message.setContent(content, "text/html" );
            if (StringUtils.isNotBlank(username)) {
                Transport.send(message, username, password);
            } else {
                Transport.send(message);
            }
        } catch (Exception e){
            logger.error("Unable to send email to " + recipient + ".\n"+content, e);
        }
    }
    
    /**
     * Sends an email from the default sender using the supplied details.
     * 
     * @param recipient
     * @param subject
     * @param content
     */
    public void sendEmail(String recipient, String subject, String content){
        sendEmail(recipient, null, subject, content, sender);
    }

    /**
     * Sends an email from the default sender using the supplied details.
     *
     * @param recipient
     * @param copy
     * @param subject
     * @param content
     */
    public void sendEmail(String recipient, String copy, String subject, String content){
        sendEmail(recipient, copy, subject, content, sender);
    }
    
    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }
    
    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }
    
    /**
     * @return the port
     */
    public String getPort() {
        return port;
    }
    
    /**
     * @param port the port to set
     */
    public void setPort(String port) {
        this.port = port;
    }
    
    /**
     * @return the sender
     */
    public String getSender() {
        return sender;
    }
    
    /**
     * @param sender the sender to set
     */
    public void setSender(String sender) {
        this.sender = sender;
    }

    /**
     * @return the starttls flag
     */
    public boolean getStarttls() {
        return starttls;
    }

    /**
     * @param starttls the starttls flag to set
     */
    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }
}