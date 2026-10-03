package mss301.carerecipientservice.enums;

// Quan hệ của người quản lý với người bệnh — đồng bộ CHECK recipient_managers.relationship_type.
public enum RelationshipType {
    SELF,
    CHILD,
    SPOUSE,
    PARENT,
    SIBLING,
    GRANDCHILD,
    RELATIVE,
    OTHER
}
