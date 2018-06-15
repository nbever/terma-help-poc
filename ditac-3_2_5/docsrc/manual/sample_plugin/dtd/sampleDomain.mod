<!--                    LONG NAME: Time                        -->
<!ENTITY % time "time">

<!ELEMENT time (#PCDATA | %text;)*>

<!ATTLIST time %univ-atts;                                  
               outputclass CDATA #IMPLIED>

<!ATTLIST time %global-atts;
               class CDATA "+ topic/ph sample-d/time ">

<!ATTLIST time %univ-atts;                                  
               datetime CDATA #IMPLIED>

<!--                    LONG NAME: Keyboard input              -->
<!ENTITY % kbd "kbd">

<!ELEMENT kbd (#PCDATA | %text;)*>

<!ATTLIST kbd %univ-atts;                                  
               outputclass CDATA #IMPLIED>

<!ATTLIST kbd %global-atts;
               class CDATA "+ topic/ph sample-d/kbd ">

