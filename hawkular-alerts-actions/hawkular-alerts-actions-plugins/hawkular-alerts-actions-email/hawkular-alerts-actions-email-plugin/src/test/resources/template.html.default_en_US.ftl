<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0"/>
    <title>Hawkular alert</title>
    <meta name="robots" content="noindex,nofollow" />
    <link rel="shortcut icon" href="https://dl.dropboxusercontent.com/u/2730435/favicon.ico" type="image/x-icon" />

    <style type="text/css">

        /* Reset styles */
        body{margin:0; padding:0;}

        /* Styles */

        a img {
            border-style:none;
        }

        a.link:hover {
            color: #00618a!important;
            text-decoration: underline!important;
        }

        a.btn-primary {
            background-image: -webkit-linear-gradient(top,#00a8e1 0,#0085cf 100%);
            background-image: -o-linear-gradient(top,#00a8e1 0,#0085cf 100%);
            background-image: linear-gradient(to bottom,#00a8e1 0,#0085cf 100%);
            background-repeat: repeat-x;
            filter: progid:DXImageTransform.Microsoft.gradient(startColorstr='#ff00a8e1', endColorstr='#ff0085cf', GradientType=0);
        }

        a.btn-primary:hover {
            background-color: #0085cf!important;
            background-image: none!important;
        }

        /* Mobile sites */
        @media only screen and (max-width: 640px){

            body{width:100% !important; min-width:100% !important;} /* Force iOS Mail to render the email at full width. */

            table[id="table-body"],
            table[class="flexible-container"]{width:100% !important;}

            .bg-container {
                padding:0!important;
            }
        }

    </style>

</head>
<body>
<div style="display:table;width:100%;border-spacing:0;border-collapse:collapse;margin:auto 0">
    <table width="100%" border="0" cellspacing="0" cellpadding="0" align="center" style="margin: 0 auto;">
        <tr>
            <td bgcolor="#f2f2f2" class="bg-container" style="padding:20px; background-color:#f2f2f2;">
                <table width="600" border="0" cellpadding="0" cellspacing="0" id="table-body" style="margin:0 auto;">

                    <!-- Email content text / Browser link -->

                    <tr>
                        <td>
                            <div style="color:transparent;font-size:0px;height:0;"><#if subject??><${subject}</#if> View more details...</div>
                        </td>
                    </tr>

                    <!-- Email content -->

                    <tr>
                        <td style="background-color:#ffffff">
                            <table width="100%" border="0" cellspacing="0" cellpadding="0" style="margin:0 auto;">

                                <!-- Header -->

                                <tr>
                                    <td>
                                        <table width="600" cellspacing="0" cellpadding="0" border="0" class="flexible-container" style="margin:0 auto;">
                                            <tr>
                                                <td style="padding:30px 30px 40px 30px;">
                                                    <a href="<#if url??>${url}<#else>http://www.hawkular.org</#if>"
                                                       target="_blank"
                                                       style="display:inline-block;">
                                                        <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAWgAAABMCAIAAADPzboWAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAA2hpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuMy1jMDExIDY2LjE0NTY2MSwgMjAxMi8wMi8wNi0xNDo1NjoyNyAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wTU09Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9tbS8iIHhtbG5zOnN0UmVmPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VSZWYjIiB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iIHhtcE1NOk9yaWdpbmFsRG9jdW1lbnRJRD0ieG1wLmRpZDowMDgwMTE3NDA3MjA2ODExODA4M0Q5RkNBQjM0RTkxNyIgeG1wTU06RG9jdW1lbnRJRD0ieG1wLmRpZDo3OUE0NTRGMTBERkQxMUU1OUY1NUE0Q0U4RkM0QTIxRiIgeG1wTU06SW5zdGFuY2VJRD0ieG1wLmlpZDo3OUE0NTRGMDBERkQxMUU1OUY1NUE0Q0U4RkM0QTIxRiIgeG1wOkNyZWF0b3JUb29sPSJBZG9iZSBQaG90b3Nob3AgQ1M2IChNYWNpbnRvc2gpIj4gPHhtcE1NOkRlcml2ZWRGcm9tIHN0UmVmOmluc3RhbmNlSUQ9InhtcC5paWQ6Rjg3RjExNzQwNzIwNjgxMTgwODNENUE0NkNCRkIyRTkiIHN0UmVmOmRvY3VtZW50SUQ9InhtcC5kaWQ6MDA4MDExNzQwNzIwNjgxMTgwODNEOUZDQUIzNEU5MTciLz4gPC9yZGY6RGVzY3Jp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" alt="Hawkular logo" border="0" width="180" height="38" style="display:block; margin:0;" />
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- Content -->

                                <tr>
                                    <td align="center"
                                        <#if alert?? && alert.status?? && alert.status == 'RESOLVED'>
                                            style="background-color:#f0f7ef; padding:40px 30px 33px 30px;"
                                        <#else>
                                            style="background-color:#faeceb; padding:40px 30px 33px 30px;"
                                        </#if>>
                                        <table width="540" cellspacing="0" cellpadding="0" border="0" class="flexible-container" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" style="padding-bottom:25px;">
                                                    <#if status?? && status == 'resolved'>
                                                    <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHgAAABoCAYAAAA6sjRJAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAA2hpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuMy1jMDExIDY2LjE0NTY2MSwgMjAxMi8wMi8wNi0xNDo1NjoyNyAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wTU09Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9tbS8iIHhtbG5zOnN0UmVmPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VSZWYjIiB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iIHhtcE1NOk9yaWdpbmFsRG9jdW1lbnRJRD0ieG1wLmRpZDowMDgwMTE3NDA3MjA2ODExODA4M0Q5RkNBQjM0RTkxNyIgeG1wTU06RG9jdW1lbnRJRD0ieG1wLmRpZDo3MDVBNDk0NTBFQkExMUU1ODIxQUFGRDlFRUJBM0ZBOSIgeG1wTU06SW5zdGFuY2VJRD0ieG1wLmlpZDo3MDVBNDk0NDBFQkExMUU1ODIxQUFGRDlFRUJBM0ZBOSIgeG1wOkNyZWF0b3JUb29sPSJBZG9iZSBQaG90b3Nob3AgQ1M2IChNYWNpbnRvc2gpIj4gPHhtcE1NOkRlcml2ZWRGcm9tIHN0UmVmOmluc3RhbmNlSUQ9InhtcC5paWQ6Rjg3RjExNzQwNzIwNjgxMTgwODNENUE0NkNCRkIyRTkiIHN0UmVmOmRvY3VtZW50SUQ9InhtcC5kaWQ6MDA4MDExNzQwNzIwNjgxMTgwODNEOUZDQUIzNEU5MTciLz4gPC9yZGY6RGVzY3Jp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
                                                    " alt="Icon: alert resolved flag" border="0" width="60"
                                                         height="52" />
                                                    <#else>
                                                    <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHcAAABoCAYAAADLuW/EAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAA2hpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuMy1jMDExIDY2LjE0NTY2MSwgMjAxMi8wMi8wNi0xNDo1NjoyNyAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wTU09Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9tbS8iIHhtbG5zOnN0UmVmPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VSZWYjIiB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iIHhtcE1NOk9yaWdpbmFsRG9jdW1lbnRJRD0ieG1wLmRpZDowMDgwMTE3NDA3MjA2ODExODA4M0Q5RkNBQjM0RTkxNyIgeG1wTU06RG9jdW1lbnRJRD0ieG1wLmRpZDo3OUE0NTRGNTBERkQxMUU1OUY1NUE0Q0U4RkM0QTIxRiIgeG1wTU06SW5zdGFuY2VJRD0ieG1wLmlpZDo3OUE0NTRGNDBERkQxMUU1OUY1NUE0Q0U4RkM0QTIxRiIgeG1wOkNyZWF0b3JUb29sPSJBZG9iZSBQaG90b3Nob3AgQ1M2IChNYWNpbnRvc2gpIj4gPHhtcE1NOkRlcml2ZWRGcm9tIHN0UmVmOmluc3RhbmNlSUQ9InhtcC5paWQ6Rjg3RjExNzQwNzIwNjgxMTgwODNENUE0NkNCRkIyRTkiIHN0UmVmOmRvY3VtZW50SUQ9InhtcC5kaWQ6MDA4MDExNzQwNzIwNjgxMTgwODNEOUZDQUIzNEU5MTciLz4gPC9yZGY6RGVzY3JpcHRpb24+IDwvcmRmOlJERj4gPC94OnhtcG1ldGE+IDw/eHBhY2tldCBlbmQ9InIiPz7dqnS3AAAD90lEQVR42uydy1XbQBRAZQ47FqgDTAU4O3bIFeBUgKkgpIKYDkgFERUEOhA7lqKCiA7sBWtnBt7EEx3byNKMPZLvPWeOjT+y9K7mK+zXm8/nUZnno16sbkZSElWO5akXVTJV0vO3eS6vjWB3KA8rn+uV5SpZWuidKiefbPdRlbEqU0IcptyDklgt63cFsZpLqcUxIQ6Tg1KN/bXh+88QHLhc6WPTmtvQgm8IZbg1d2wNmuqA3IDljhpu59jBNsCT3AsH2xoQzrA47Mhx9KUYEut+Zt0vpHQGNV7qS6upjytXU6OirXJjaSESue3LgG4dP5Y89irB0OJzKW2TrmMwKbe6SvatEjyx5b5WnNuuo/B4EGal7MzRNk+kXJSEa9kPUlol1T6Z9aqhFvy+QqX+0NOgq4YfeupQ8MgqxzsK4qNITgOR2hepVTzNlNzYyNVv/NPgg59K/VzdM3K8Y6FLAyWS76T5DlmqzfDf2rISrHf+W82DH9SstbEIvXHQLWyDF5GsZU8DlbpUbix9zqb92nWNpsvseGi1dJMTOhXRrscapgVr2k0O/7sqZC1DXlY8wPGGg49Eaull1B3uHTTZ5hLrjcNB47C34npuVmFhY6g67azi9dzPRnhd4Mlqsqu2XmYm4ONkH/qe5+6DVMOFFDOlKkoLKGahZSDF+xjDl9yBnMX7IHXZHPpqzQLK1vAht+6oGwKX62IxBBxx4HBbY8R2V25COLsrt084uysXkAvIBeQCcpELyAXkAnIBuYBcQC5yAbmAXEAuIBeQi1xALiAXkAvIBeQCcpELyAXkAnIBuYBcQC5yAbmAXEAuIBeQi1xALiAXkAseyZHbTX6ev82nXUj3pn+Z3M7qVcjjWel1JpuYJokWP0N/0kGx71lR2yhXp395iBap2qqmgJlawm3xRrKvXAPbQudYmOi8E+aBtsg12bl85PPRNT2V4iNLyNaltkGuT6HrarcRrWvzOAr3B8JXSg11tKz7z9voIy/gSII83dG+ZCJX78t9YFKHcvJlbZgK6R3+Gi0yhRUBBbMoSZ7tYB9m8tlfqkgNpVl2kU1r25Jj6ZN18Z2qrlHuQJc192FDqacSrDaILffLE5F8LWMD163Yd4nPoEnX5LLmptHn2TTvA2x2mx5zWppOJRvU6Jmc3Jl162yMcej4jDaDoLOOS103nVq2YGK/zsQgjzwPFl33uXm0yClvOv4s2j9WLZhsFV8Dqn2VGhRcOEAuIBeQC8gF5AJykQvIBeQCcgG5gFxALnIBuYBcQC4gF5CLXEAudENuUeG9BeFrp9xJ9PF1ylXcnr/NkRs4S/8pXcT1n496+tsDcenpXP+YBqELn78CDADNhAorEgKyfAAAAABJRU5ErkJggg==" alt="Icon: alert flag" border="0" width="60" height="52" />
                                                    </#if>
                                                </td>
                                            </tr>
                                            <#list conditions as condition>
                                                <tr>
                                                    <td align="center" style="color:#333333; font-family:Open sans,sans-serif; font-size:15px; font-weight:bold; line-height:24px;">
                                                        ${condition.description}
                                                    </td>
                                                </tr>
                                                <#if condition.averageDescription??>
                                                    <tr>
                                                        <td align="center" style="color:#999999; font-family:Open sans,sans-serif; font-size:13px; line-height:21px; padding-bottom:7px;">
                                                        (Average ${condition.averageDescription})
                                                        </td>
                                                    </tr>
                                                </#if>
                                            </#list>
                                            <tr>
                                                <td align="center" style="line-height:24px; padding-bottom:15px;">
                                                    <a class="link" href="<#if baseUrl??>${baseUrl}<#else>http://www.hawkular.org</#if>" target="_blank"
                                                       style="color:#0099d3; font-family:Open sans,sans-serif;
                                                       font-size:15px; text-decoration: none;"><#if triggerDescription??>${triggerDescription}</#if></a>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td align="center" style="color:#333333; font-family:Open sans,sans-serif; font-size:13px; line-height:21px;">
                                                    Start time: <span style="font-size:15px;"><#if event??>${event.ctime?number_to_datetime}</#if></span>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td align="center" style="color:#999999; font-family:Open sans,sans-serif; font-size:13px; line-height:21px; padding-bottom:7px;">
                                                <#if dampeningDescription??>${dampeningDescription}</#if>
                                                </td>
                                            </tr>
                                            <#if alert?? && alert.status?? && alert.status == 'ACKNOWLEDGED'>
                                                <tr>
                                                    <td align="center" style="color:#333333; font-family:Open sans,sans-serif; font-size:13px; line-height:21px;">
                                                        Acknowledge time:
                                                        <span style="font-size:15px;">${alert.currentLifecycle.stime?number_to_datetime}</span>
                                                    </td>
                                                </tr>
                                                <#if alert.currentLifecycle.user?? >
                                                    <tr>
                                                        <td align="center" style="color:#999999; font-family:Open
                                                        sans,sans-serif; font-size:13px; line-height:21px;
                                                        padding-bottom:2px;">
                                                            by ${alert.currentLifecycle.user}
                                                        </td>
                                                    </tr>
                                                </#if>
                                            </#if>
                                            <#if alert?? && alert.status?? && alert.status == 'RESOLVED'>
                                                <tr>
                                                    <td align="center" style="color:#333333; font-family:Open sans,sans-serif; font-size:13px; line-height:21px;">
                                                        Resolved time:
                                                        <span style="font-size:15px;">${alert.currentLifecycle.stime?number_to_datetime}</span>
                                                    </td>
                                                </tr>
                                                <#if alert.currentLifecycle.user?? >
                                                    <tr>
                                                        <td align="center" style="color:#999999; font-family:Open
                                                            sans,sans-serif; font-size:13px; line-height:21px;
                                                            padding-bottom:2px;">
                                                            by ${alert.currentLifecycle.user}
                                                        </td>
                                                    </tr>
                                                </#if>
                                            </#if>
                                            <#if alert?? && alert.notes?? && alert.notes?has_content >
                                                <#list alert.notes as note>
                                                 <#if note.text?? && note.user??>
                                                    <tr>
                                                        <td align="center" style="color:#999999; font-family:Open sans,sans-serif; font-size:13px; line-height:21px; padding-bottom:7px;">
                                                          ${note.text}
                                                          <br>(${note.user}, ${note.ctime?number_to_datetime})
                                                        </td>
                                                    </tr>
                                                 </#if>
                                                </#list>
                                            </#if>
                                        </table>
                                    </td>
                                </tr>

                                <!-- View -->
                                <#if baseUrl??>
                                <tr>
                                    <td>
                                        <table width="600" cellspacing="0" cellpadding="0" border="0" class="flexible-container" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" style="color:#999999; font-family:Open sans,sans-serif; font-size:15px; line-height:20px; padding:40px 30px 30px 30px;">
                                                    To view metrics of this alert, access your Hawkular account.
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding-bottom:50px;">
                                        <a class="btn-primary" href="${baseUrl}" target="_blank" style="display:block; background-color:#0085cf; border:1px solid #006e9c; border-radius:1px; color:#ffffff; font-family:Open sans,sans-serif; font-size:14px; font-weight:600; line-height:1.3333333; padding:6px 10px; text-decoration:none; width: 100px;">
                                            View Metrics
                                        </a>
                                    </td>
                                </tr>
                                </#if>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</div>
</body>
</html>