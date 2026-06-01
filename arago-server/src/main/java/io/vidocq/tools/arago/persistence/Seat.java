package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A seat held by an attendee in a LAB room (cf. arago-spec §4.5). First-come-first-serve: at most one
 * <em>active</em> ({@code released = false}) seat per coordinate per room, enforced by a partial
 * unique index ({@code ux_seats_active}); and at most one active seat per attendee pseudo. Releasing
 * (move, explicit release, or leaving the room) flips {@code released} so the coordinate frees up.
 *
 * <p>Coordinates are 0-indexed {@code (seatRow, seatBlockIndex, seatInBlock)} within the room's
 * {@code LayoutSpec}.</p>
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "attendee_pseudo", nullable = false)
    private String attendeePseudo;

    @Column(name = "seat_row", nullable = false)
    private int seatRow;

    @Column(name = "seat_block_index", nullable = false)
    private int seatBlockIndex;

    @Column(name = "seat_in_block", nullable = false)
    private int seatInBlock;

    @Column(nullable = false)
    private boolean released;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    public Seat() {}

    public Seat(String id, String roomId, String attendeePseudo, int seatRow, int seatBlockIndex,
                int seatInBlock, Instant takenAt) {
        this.id             = id;
        this.roomId         = roomId;
        this.attendeePseudo = attendeePseudo;
        this.seatRow        = seatRow;
        this.seatBlockIndex = seatBlockIndex;
        this.seatInBlock    = seatInBlock;
        this.released       = false;
        this.takenAt        = takenAt;
    }

    public String  getId()                          { return id;             }
    public void    setId(String id)                 { this.id = id;          }
    public String  getRoomId()                      { return roomId;         }
    public void    setRoomId(String roomId)         { this.roomId = roomId;  }
    public String  getAttendeePseudo()              { return attendeePseudo; }
    public void    setAttendeePseudo(String p)      { this.attendeePseudo = p; }
    public int     getSeatRow()                     { return seatRow;        }
    public void    setSeatRow(int seatRow)          { this.seatRow = seatRow; }
    public int     getSeatBlockIndex()              { return seatBlockIndex; }
    public void    setSeatBlockIndex(int i)         { this.seatBlockIndex = i; }
    public int     getSeatInBlock()                 { return seatInBlock;    }
    public void    setSeatInBlock(int seatInBlock)  { this.seatInBlock = seatInBlock; }
    public boolean isReleased()                     { return released;       }
    public void    setReleased(boolean released)    { this.released = released; }
    public Instant getTakenAt()                     { return takenAt;        }
    public void    setTakenAt(Instant takenAt)      { this.takenAt = takenAt; }
    public Instant getReleasedAt()                  { return releasedAt;     }
    public void    setReleasedAt(Instant at)        { this.releasedAt = at;  }
}
