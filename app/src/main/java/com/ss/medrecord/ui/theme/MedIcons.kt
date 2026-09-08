package com.ss.medrecord.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ss.medrecord.R

/**
 * The design draws its icons with Material Symbols Rounded, and so does this.
 *
 * They are glyphs in a bundled font rather than vector assets, for two
 * reasons. The design names icons that are not in Compose's core icon set -
 * stethoscope, medication, fingerprint, call_split - and the extended icon
 * artifact is not a dependency this project carries. And the font, subsetted
 * to only the glyphs used here, is around 30 KB: smaller than the equivalent
 * vector drawables, and impossible to get visually out of step with the
 * design, since it is the same typeface the design was drawn with.
 *
 * A glyph is addressed by its ligature name rather than by codepoint. The
 * published codepoint table is written against the variable font, and the
 * subset Google returns for a named icon set does not agree with it for every
 * icon - fifteen of these rendered as empty boxes when addressed numerically.
 * The names are what the subset is actually built around, so they are what is
 * used, and `DesignCaptureTest.captureIconSheet` renders all of them so a
 * missing one shows up as a word on a page rather than in a shipped build.
 */
val MaterialSymbols = FontFamily(
    Font(R.font.material_symbols_rounded, FontWeight.Normal),
)

/**
 * One icon: the glyph to draw, and what it should be announced as.
 *
 * The label travels with the glyph rather than being passed at every call
 * site, because an icon font renders as text - without an explicit content
 * description a screen reader would read out whatever private-use character
 * sits at that codepoint.
 */
@Immutable
data class MedIcon(val glyph: String, val label: String)

/** Every icon the app draws, named as the design names them. */
object MedIcons {
    val Add = MedIcon("add", "add")
    val Alarm = MedIcon("alarm", "alarm")
    val ArrowBack = MedIcon("arrow_back", "arrow back")
    val ArrowForward = MedIcon("arrow_forward", "arrow forward")
    val AttachFile = MedIcon("attach_file", "attach file")
    val CallSplit = MedIcon("call_split", "call split")
    val Check = MedIcon("check", "check")
    val CheckCircle = MedIcon("check_circle", "check circle")
    val ChevronRight = MedIcon("chevron_right", "chevron right")
    val Close = MedIcon("close", "close")
    val CloudUpload = MedIcon("cloud_upload", "cloud upload")
    val Delete = MedIcon("delete", "delete")
    val Description = MedIcon("description", "description")
    val Directions = MedIcon("directions", "directions")
    val Download = MedIcon("download", "download")
    val Edit = MedIcon("edit", "edit")
    val Event = MedIcon("event", "event")
    val EventRepeat = MedIcon("event_repeat", "event repeat")
    val ExpandLess = MedIcon("expand_less", "expand less")
    val ExpandMore = MedIcon("expand_more", "expand more")
    val FilterList = MedIcon("filter_list", "filter list")
    val Fingerprint = MedIcon("fingerprint", "fingerprint")
    val FolderOpen = MedIcon("folder_open", "folder open")
    val GridView = MedIcon("grid_view", "grid view")
    val HealthAndSafety = MedIcon("health_and_safety", "health and safety")
    val History = MedIcon("history", "history")
    val Image = MedIcon("image", "image")
    val Info = MedIcon("info", "info")
    val LocalHospital = MedIcon("local_hospital", "local hospital")
    val LocalPharmacy = MedIcon("local_pharmacy", "local pharmacy")
    val LocationOn = MedIcon("location_on", "location on")
    val Lock = MedIcon("lock", "lock")
    val Logout = MedIcon("logout", "logout")
    val Medication = MedIcon("medication", "medication")
    val MoreHoriz = MedIcon("more_horiz", "more horiz")
    val MyLocation = MedIcon("my_location", "my location")
    val Notifications = MedIcon("notifications", "notifications")
    val Pause = MedIcon("pause", "pause")
    val Person = MedIcon("person", "person")
    val PersonAdd = MedIcon("person_add", "person add")
    val PictureAsPdf = MedIcon("picture_as_pdf", "picture as pdf")
    val PriorityHigh = MedIcon("priority_high", "priority high")
    val ReceiptLong = MedIcon("receipt_long", "receipt long")
    val Refresh = MedIcon("refresh", "refresh")
    val Schedule = MedIcon("schedule", "schedule")
    val Search = MedIcon("search", "search")
    val Settings = MedIcon("settings", "settings")
    val Share = MedIcon("share", "share")
    val Shield = MedIcon("shield", "shield")
    val Stethoscope = MedIcon("stethoscope", "stethoscope")
    val Sync = MedIcon("sync", "sync")
    val Tune = MedIcon("tune", "tune")
    val VerifiedUser = MedIcon("verified_user", "verified user")
    val Visibility = MedIcon("visibility", "visibility")
    val VisibilityOff = MedIcon("visibility_off", "visibility off")
    val Warning = MedIcon("warning", "warning")
    val WifiOff = MedIcon("wifi_off", "wifi off")
    val Cancel = MedIcon("cancel", "cancel")
    val DoneAll = MedIcon("done_all", "done all")
    val OpenInNew = MedIcon("open_in_new", "open in new")
    val Call = MedIcon("call", "call")
    val Map = MedIcon("map", "map")
    val Science = MedIcon("science", "science")
    val PhotoCamera = MedIcon("photo_camera", "photo camera")
    val UploadFile = MedIcon("upload_file", "upload file")
    val Vaccines = MedIcon("vaccines", "vaccines")
    val MonitorHeart = MedIcon("monitor_heart", "monitor heart")
    val Bloodtype = MedIcon("bloodtype", "bloodtype")
    val WaterDrop = MedIcon("water_drop", "water drop")
    val Star = MedIcon("star", "star")
    val Help = MedIcon("help", "help")
    val DarkMode = MedIcon("dark_mode", "dark mode")
    val LightMode = MedIcon("light_mode", "light mode")
    val Contrast = MedIcon("contrast", "contrast")
}
