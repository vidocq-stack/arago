package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A room — the central aggregate of Arago (cf. arago-spec §4.1, §7). Phase 1 adds the lifecycle
 * fields needed to create, own and end a room: the owning speaker ({@code ownerSub} = the OIDC
 * subject), the presentation {@code mode}, and {@code endedAt}. The richer model (layout,
 * co-speakers, reveal secret…) lands later.
 *
 * <p>The {@code id} is an application-assigned UUID string; the {@code pin} is unique among
 * {@code DRAFT + ACTIVE} rooms (enforced by a partial index in the migration).
 */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    private String id;

    @Column(nullable = false)
    private String pin;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status;

    /** OIDC subject ({@code sub}) of the speaker who created/owns the room. */
    @Column(name = "owner_sub", nullable = false)
    private String ownerSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomMode mode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Set when the room transitions to {@code ENDED}; null while live. */
    @Column(name = "ended_at")
    private Instant endedAt;

    public Room() {}

    public Room(String id, String pin, String title, RoomStatus status, RoomMode mode,
                String ownerSub, Instant createdAt) {
        this.id        = id;
        this.pin       = pin;
        this.title     = title;
        this.status    = status;
        this.mode      = mode;
        this.ownerSub  = ownerSub;
        this.createdAt = createdAt;
    }

    public String     getId()                       { return id;            }
    public void       setId(String id)              { this.id = id;         }
    public String     getPin()                      { return pin;           }
    public void       setPin(String pin)            { this.pin = pin;       }
    public String     getTitle()                    { return title;         }
    public void       setTitle(String title)        { this.title = title;   }
    public RoomStatus getStatus()                   { return status;        }
    public void       setStatus(RoomStatus status)  { this.status = status; }
    public String     getOwnerSub()                 { return ownerSub;      }
    public void       setOwnerSub(String ownerSub)  { this.ownerSub = ownerSub; }
    public RoomMode   getMode()                     { return mode;          }
    public void       setMode(RoomMode mode)        { this.mode = mode;     }
    public Instant    getCreatedAt()                { return createdAt;     }
    public void       setCreatedAt(Instant at)      { this.createdAt = at;  }
    public Instant    getEndedAt()                  { return endedAt;       }
    public void       setEndedAt(Instant endedAt)   { this.endedAt = endedAt; }
}
