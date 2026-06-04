package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A co-speaker allowed to co-manage a room (cf. arago-spec §17.3). The room owner (primary admin) is
 * NOT stored here — only the invited co-speakers. Authorization matches the caller's allowlist email
 * ({@code speakerEmail}); {@code speakerSub} is recorded opportunistically once known.
 */
@Entity
@Table(name = "room_managers")
public class RoomManager {

    @Id
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    /** Lowercased allowlist email of the co-speaker (the matching key). */
    @Column(name = "speaker_email", nullable = false)
    private String speakerEmail;

    @Column(name = "speaker_sub")
    private String speakerSub;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public RoomManager() {}

    public RoomManager(String id, String roomId, String speakerEmail, String speakerSub, Instant addedAt) {
        this.id           = id;
        this.roomId       = roomId;
        this.speakerEmail = speakerEmail;
        this.speakerSub   = speakerSub;
        this.addedAt      = addedAt;
    }

    public String  getId()                    { return id;           }
    public void    setId(String id)           { this.id = id;        }
    public String  getRoomId()                { return roomId;       }
    public void    setRoomId(String roomId)   { this.roomId = roomId; }
    public String  getSpeakerEmail()          { return speakerEmail; }
    public void    setSpeakerEmail(String e)  { this.speakerEmail = e; }
    public String  getSpeakerSub()            { return speakerSub;   }
    public void    setSpeakerSub(String s)    { this.speakerSub = s; }
    public Instant getAddedAt()               { return addedAt;      }
    public void    setAddedAt(Instant at)     { this.addedAt = at;   }
}
