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

    @Column(nullable = false)
    private boolean persistent;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "at", nullable = false)
    private Instant at;

    /** When an ephemeral message becomes eligible for purge; null for persistent messages. */
    @Column(name = "purge_after")
    private Instant purgeAfter;

    public ChatMessage() {}

    public ChatMessage(String id, String roomId, String profileId, String authorPseudo,
                       boolean fromSpeaker, boolean persistent, String body, Instant at,
                       Instant purgeAfter) {
        this.id           = id;
        this.roomId       = roomId;
        this.profileId    = profileId;
        this.authorPseudo = authorPseudo;
        this.fromSpeaker  = fromSpeaker;
        this.persistent   = persistent;
        this.body         = body;
        this.at           = at;
        this.purgeAfter   = purgeAfter;
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
    public boolean isPersistent()                { return persistent;   }
    public void    setPersistent(boolean p)      { this.persistent = p; }
    public String  getBody()                     { return body;         }
    public void    setBody(String body)          { this.body = body;    }
    public Instant getAt()                       { return at;           }
    public void    setAt(Instant at)             { this.at = at;        }
    public Instant getPurgeAfter()               { return purgeAfter;   }
    public void    setPurgeAfter(Instant after)  { this.purgeAfter = after; }
}
