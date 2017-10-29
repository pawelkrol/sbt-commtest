;-------------------------------------------------------------------------
; PRECONDITIONS:
; Do not run in parallel! Only one instance of this subroutine is allowed,
; since it modifies its own source code dynamically!
;
; INPUT:
; .X -> X coordinate (0..160)
; .Y -> Y coordinate (0..200)
; .A -> offset of 2 zero-page variables used for processing
;
; OUTPUT:
; .A -> X/Y multi bitmap color
;
; EXAMPLE:
; BITMAP  = $4000
; SCREEN  = $6800
; COLORS  = $d800
; #include "get-color-multi.inc"
; ldx #$01
; ldy #$01
; lda #$fb
; jsr get_multi_bitmap_color
;-------------------------------------------------------------------------
get_multi_bitmap_color

                stx _getmcol_x
                sty _getmcol_y
                sta _getmcol_z1_lo+1
                sta _getmcol_z2_lo+1
                sta _getmcol_z3_lo+1
                sta _getmcol_z4_lo+1
                sta _getmcol_z5_lo+1
                clc
                adc #$01
                sta _getmcol_z1_hi+1
                sta _getmcol_z2_hi+1
                sta _getmcol_z3_hi+1
                sta _getmcol_z4_hi+1
                lda #$ea ; NOP
                sta _getmcol_code1
                sta _getmcol_code2
                sta _getmcol_code3
                sta _getmcol_code4

                ; Convert X/Y coordinates to memory address of bitmap data:
                ldx _getmcol_x
                ldy _getmcol_y
                lda bitmap_y_addr_hi,y
                clc
                adc bitmap_x_offs_hi,x
_getmcol_z1_hi  sta $01
                lda bitmap_y_addr_lo,y
                clc
                adc bitmap_x_offs_lo,x
_getmcol_z1_lo  sta $00
                bcc *+4
_getmcol_z2_hi  inc $01

                ; Convert X coordinate to bit mask of bitmap data
                lda _getmcol_x
                and #%00000011
                tax
                lda _getmcol_bitmsk,x
                sta _getmcol_andmsk+1
                lda _getmcol_valrol,x
                tax
                ; Get plot value at calculated bitmap position:
                ldy #$00
_getmcol_z2_lo  lda ($00),y
_getmcol_andmsk and #$00
                dex
                bmi *+7
                lsr
                lsr
                jmp *-5

                ; This value defines where color should be taken from!
                ; 00 = Background ($D021)
                ; 01 = HN Screen-RAM
                ; 10 = LN Screen-RAM
                ; 11 = Color-RAM
                sta _getmcol_colsrc
                cmp #$00
                bne _getmcol_colreq
                lda $d021
                and #$0f
                rts
_getmcol_colreq eor #%00000011
                beq _getmcol_calccl

                ; Convert X/Y coordinates to memory address of screen data:
                lda _getmcol_y
                lsr
                lsr
                lsr
                tay
                lda screen_y_addr_lo,y
_getmcol_z5_lo  sta $00
                lda screen_y_addr_hi,y
_getmcol_z4_hi  sta $01
                lda _getmcol_colsrc
                cmp #%00000010
                beq *+16
                lda #$4a ; LSR
                sta _getmcol_code1
                sta _getmcol_code2
                sta _getmcol_code3
                sta _getmcol_code4

                jmp _getmcol_getcol

_getmcol_calccl ; Convert X/Y coordinates to memory address of colors data:
                lda _getmcol_y
                lsr
                lsr
                lsr
                tay
                lda colors_y_addr_lo,y
_getmcol_z3_lo  sta $00
                lda colors_y_addr_hi,y
_getmcol_z3_hi  sta $01

_getmcol_getcol lda _getmcol_x
                lsr ; dividing x position by 4, because each of my virtual 1..4
                lsr ; x positions are only 1 position on a real screen data!
                tay
_getmcol_z4_lo  lda ($00),y
_getmcol_code1  nop
_getmcol_code2  nop
_getmcol_code3  nop
_getmcol_code4  nop
                and #$0f

                rts
;-------------------------------------------------------------------------
_getmcol_x      .db $00
_getmcol_y      .db $00
_getmcol_bitmsk .db %11000000,%00110000,%00001100,%00000011
_getmcol_valrol .db $03,$02,$01,$00
_getmcol_colsrc .db $00
;-------------------------------------------------------------------------
#include "graphic-tables.inc"
;-------------------------------------------------------------------------