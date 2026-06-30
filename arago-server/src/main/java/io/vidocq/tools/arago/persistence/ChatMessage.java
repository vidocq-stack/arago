package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A chat message in a room (cf. arago-spec §4.3). References are stored as plain id columns
 * ({@code roomId}, optional {@code profileId}) — consistent with the rest of the Arago model.
 *
 * <p>Retention (§4.7): ephemeral messages ({@code persistent=false}) carry a {@code purgeAfter}
 * instant and are removed by the daily purge once past it; {@code persistent=true} messages
 * (an attendee who provided an email may flag a message) survive room closure and have a null
 * {@code purgeAfter}.</p>
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    /** Attendee profile id when the author provided an email; null for a pseudo-only author. */
    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "author_pseudo", nullable = false)
    private String authorPseudo;

    @Column(name = "from_speaker", nullable = false)
    private boolean fromSpeaker;

    /**
     * Owner of the private (DM) thread — the attendee pseudo this message belongs to; {@code null} for a
     * normal global room message. A DM is delivered to that attendee plus every speaker of the room (§4.3).
     */
    @Column(name = "dm_attendee")
    private String dmAttendee;

    @Column(nullable = false)
    private boolean persistent;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "at", nullable = false)
    private Instant at;

    /** When an ephemeral message becomes eligible for purge; null for persistent messages. */
    @Column(name = "purge_after")
    private Instant purgeAfter;

    /**
     * Whether a persistent message is "active". A persistent message from an attendee whose email is
     * not yet validated stays {@code false} (held pending, §4.7/§10.1) until they follow the validation
     * magic link; ephemeral messages are always {@code true}.
     */
    @Column(nullable = false)
    private boolean validated;

    /** Optional attachment (§4.3): its id (served at {@code /api/attachments/{id}}), kind and filename. */
    @Column(name = "attachment_id")
    private String attachmentId;

    @Column(name = "attachment_kind")
    private String attachmentKind;

    @Column(name = "attachment_name")
    private String attachmentName;

    public ChatMessage() {}

    public ChatMessage(String id, String roomId, String profileId, String authorPseudo,
                       boolean fromSpeaker, boolean persistent, String body, Instant at,
                       Instant purgeAfter, boolean validated) {
        this.id           = id;
        this.roomId       = roomId;
        this.profileId    = profileId;
        this.authorPseudo = authorPseudo;
        this.fromSpeaker  = fromSpeaker;
        this.persistent   = persistent;
        this.body         = body;
        this.at           = at;
        this.purgeAfter   = purgeAfter;
        this.validated    = validated;
    }

    public String  getId()                       { return id;           }
    public void    setId(String id)              { this.id = id;        }
    public String  getRoomId()                   { return roomId;       }
    public void    setRoomId(String roomId)      { this.roomId = roomId; }
    public String  getProfileId()                { return profileId;    }
    public void    setProfileId(String id)       { this.profileId = id; }
    public String  getAuthorPseudo()             { return authorPseudo; }
    public void    setAuthorPseudo(String p)     { this.authorPseudo = p; }
    public boolean isFromSpeaker()               { return fromSpeaker;  }
    public void    setFromSpeaker(boolean f)     { this.fromSpeaker = f; }
    public String  getDmAttendee()               { return dmAttendee;   }
    public void    setDmAttendee(String a)       { this.dmAttendee = a; }
    public boolean isPersistent()                { return persistent;   }
    public void    setPersistent(boolean p)      { this.persistent = p; }
    public String  getBody()                     { return body;         }
    public void    setBody(String body)          { this.body = body;    }
    public Instant getAt()                       { return at;           }
    public void    setAt(Instant at)             { this.at = at;        }
    public Instant getPurgeAfter()               { return purgeAfter;   }
    public void    setPurgeAfter(Instant after)  { this.purgeAfter = after; }
    public boolean isValidated()                 { return validated;    }
    public void    setValidated(boolean v)       { this.validated = v;  }
    public String  getAttachmentId()             { return attachmentId;   }
    public void    setAttachmentId(String id)    { this.attachmentId = id; }
    public String  getAttachmentKind()           { return attachmentKind; }
    public void    setAttachmentKind(String k)   { this.attachmentKind = k; }
    public String  getAttachmentName()           { return attachmentName; }
    public void    setAttachmentName(String n)   { this.attachmentName = n; }
}
