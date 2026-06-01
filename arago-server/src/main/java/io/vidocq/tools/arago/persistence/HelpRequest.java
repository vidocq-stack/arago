package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A "need help" request raised by an attendee in a LAB room (cf. arago-spec §4.5). Real-time: the
 * attendee raises it over the room WebSocket; the speaker claims/resolves it; state changes are
 * broadcast back to the room.
 *
 * <p>{@code position} is an optional free-form seat hint (the full seat-assignment layout is a later
 * slice; for now an attendee may include a seat label or it stays null). {@code message} is an
 * optional ≤140-char note. One active request (PENDING/CLAIMED) per attendee at a time (§4.5).</p>
 */
@Entity
@Table(name = "help_requests")
public class HelpRequest {

    @Id
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "attendee_pseudo", nullable = false)
    private String attendeePseudo;

    @Column(name = "position")
    private String position;

    @Column(name = "message", length = 140)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HelpStatus status;

    /** OIDC subject of the speaker who claimed it; null while PENDING. */
    @Column(name = "claimed_by")
    private String claimedBy;

    /** Seat coordinates snapshotted at raise time (§4.5); null when the requester is unseated. */
    @Column(name = "seat_row")
    private Integer seatRow;

    @Column(name = "seat_block_index")
    private Integer seatBlockIndex;

    @Column(name = "seat_in_block")
    private Integer seatInBlock;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HelpRequest() {}

    public HelpRequest(String id, String roomId, String attendeePseudo, String position,
                       String message, HelpStatus status, Instant createdAt) {
        this.id             = id;
        this.roomId         = roomId;
        this.attendeePseudo = attendeePseudo;
        this.position       = position;
        this.message        = message;
        this.status         = status;
        this.createdAt      = createdAt;
        this.updatedAt      = createdAt;
    }

    public String     getId()                       { return id;             }
    public void       setId(String id)              { this.id = id;          }
    public String     getRoomId()                   { return roomId;         }
    public void       setRoomId(String roomId)      { this.roomId = roomId;  }
    public String     getAttendeePseudo()           { return attendeePseudo; }
    public void       setAttendeePseudo(String p)   { this.attendeePseudo = p; }
    public String     getPosition()                 { return position;       }
    public void       setPosition(String position)  { this.position = position; }
    public String     getMessage()                  { return message;        }
    public void       setMessage(String message)    { this.message = message; }
    public HelpStatus getStatus()                   { return status;         }
    public void       setStatus(HelpStatus status)  { this.status = status;  }
    public String     getClaimedBy()                { return claimedBy;      }
    public void       setClaimedBy(String claimedBy){ this.claimedBy = claimedBy; }
    public Instant    getCreatedAt()                { return createdAt;      }
    public void       setCreatedAt(Instant at)      { this.createdAt = at;   }
    public Instant    getUpdatedAt()                { return updatedAt;      }
    public void       setUpdatedAt(Instant at)      { this.updatedAt = at;   }
    public Integer    getSeatRow()                  { return seatRow;        }
    public void       setSeatRow(Integer seatRow)   { this.seatRow = seatRow; }
    public Integer    getSeatBlockIndex()           { return seatBlockIndex; }
    public void       setSeatBlockIndex(Integer i)  { this.seatBlockIndex = i; }
    public Integer    getSeatInBlock()              { return seatInBlock;    }
    public void       setSeatInBlock(Integer seat)  { this.seatInBlock = seat; }
}
