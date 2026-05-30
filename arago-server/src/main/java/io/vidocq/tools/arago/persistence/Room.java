package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A room — the central aggregate of Arago. Phase 0 carries only the minimal set of fields needed
 * to prove the persistence pipeline (PostgreSQL + Mansart + Flyway). The full model (mode, layout,
 * co-speakers, reveal secret…) lands in later phases (cf. arago-spec §7).
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Room() {}

    public Room(String id, String pin, String title, RoomStatus status, Instant createdAt) {
        this.id        = id;
        this.pin       = pin;
        this.title     = title;
        this.status    = status;
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
    public Instant    getCreatedAt()                { return createdAt;     }
    public void       setCreatedAt(Instant at)      { this.createdAt = at;  }
}
