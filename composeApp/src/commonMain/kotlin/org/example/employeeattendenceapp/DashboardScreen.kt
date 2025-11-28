package org.example.employeeattendenceapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import employeeattendanceapp.composeapp.generated.resources.Res
import employeeattendanceapp.composeapp.generated.resources.logo
import org.example.employeeattendenceapp.Navigation.DashboardComponent
import org.jetbrains.compose.resources.painterResource

@Composable
fun DashboardSection(component: DashboardComponent) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Adjusted to SpaceAround
    ) {

        // Logo and Company Name
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.logo), // Replace with your logo resource
                contentDescription = "Company Logo",
                modifier = Modifier.size(200.dp) // Adjusted size
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "BPG Renewables Pvt. Ltd.",
                fontSize = 20.sp,
                style = MaterialTheme.typography.headlineSmall, // Adjusted style
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Management System",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        // Role Selection
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Select Your Role",
                fontSize = 20.sp,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Button(
                onClick = component::onAdminClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)) // Google Blue
            ) {
                Text(text = "Admin", color = Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(  // Use OutlinedButton for the second button
                onClick = component::onEmployeeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text(text = "Employee")
            }

        }

        // Footer
        Text(
            text = "© 2023 Company Name. All rights reserved.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}