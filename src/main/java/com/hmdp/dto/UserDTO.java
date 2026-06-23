package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;

    public UserDTO(String nickName, Long id, String icon) {
        this.nickName = nickName;
        this.id = id;
        this.icon = icon;
    }
}
