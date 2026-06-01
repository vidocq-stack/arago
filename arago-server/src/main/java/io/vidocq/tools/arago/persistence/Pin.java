package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A piece of pinned content in a room (cf. arago-spec §4.4). Visible to everyone in the room and
 * ordered for display. {@code SECRET} pins are purged when the room closes and their content is
 * never logged.
 */
@Entity
@Table(name = "pins")
public class Pin {

    @Id
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PinType type;

    @Column(nullable = false, length = 8000)
    private String content;

    /** Highlight language for {@code CODE} pins; null otherwise. */
    @Column(name = "lang")
    private String lang;

    /** Display order within the room (ascending). */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Pin() {}

    public Pin(String id, String roomId, PinType type, String content, String lang,
               int orderIndex, Instant createdAt) {
        this.id         = id;
        this.roomId     = roomId;
        this.type       = type;
        this.content    = content;
        this.lang       = lang;
        this.orderIndex = orderIndex;
        this.createdAt  = createdAt;
    }

    public String  getId()                      { return id;          }
    public void    setId(String id)             { this.id = id;       }
    public String  getRoomId()                  { return roomId;      }
    public void    setRoomId(String roomId)     { this.roomId = roomId; }
    public PinType getType()                    { return type;        }
    public void    setType(PinType type)        { this.type = type;   }
    public String  getContent()                 { return content;     }
    public void    setContent(String content)   { this.content = content; }
    public String  getLang()                    { return lang;        }
    public void    setLang(String lang)         { this.lang = lang;   }
    public int     getOrderIndex()              { return orderIndex;  }
    public void    setOrderIndex(int orderIndex){ this.orderIndex = orderIndex; }
    public Instant getCreatedAt()               { return createdAt;   }
    public void    setCreatedAt(Instant at)     { this.createdAt = at; }
}
